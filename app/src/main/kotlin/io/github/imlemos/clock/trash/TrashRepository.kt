package io.github.imlemos.clock.trash

import io.github.imlemos.clock.alarm.data.toAlarm
import io.github.imlemos.clock.alarm.data.toEntity
import io.github.imlemos.clock.alarm.data.toGroup
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.alarm.model.AlarmGroup
import io.github.imlemos.clock.core.data.db.AlarmDao
import io.github.imlemos.clock.core.data.db.AlarmEntity
import io.github.imlemos.clock.core.data.db.AlarmGroupDao
import io.github.imlemos.clock.core.data.db.AlarmGroupEntity
import io.github.imlemos.clock.core.data.db.LayoutEntity
import io.github.imlemos.clock.core.data.db.TrashDao
import io.github.imlemos.clock.core.data.db.TrashEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Something in the trash. */
sealed interface TrashedItem {
    val trashId: Long
    val deletedAt: Instant
    val automatic: Boolean
}

data class TrashedAlarm(
    override val trashId: Long,
    val alarm: Alarm,
    override val deletedAt: Instant,
    override val automatic: Boolean,
) : TrashedItem

/**
 * A deleted group. [alarms] were deleted with it; [memberIds] are alarms that stayed (deleting only
 * the group) and rejoin it on restore if they still exist.
 */
data class TrashedGroup(
    override val trashId: Long,
    val group: AlarmGroup,
    val alarms: List<Alarm>,
    val memberIds: List<Long>,
    override val deletedAt: Instant,
) : TrashedItem {
    override val automatic: Boolean get() = false
}

/**
 * A deleted alarm screen layout (docs/PLANO.md 3.7). [alarmIds] used it and [wasDefault] tells
 * whether it was the default; both are put back on restore when nothing replaced them meanwhile.
 */
data class TrashedLayout(
    override val trashId: Long,
    val layout: LayoutEntity,
    val alarmIds: List<Long>,
    val wasDefault: Boolean,
    override val deletedAt: Instant,
) : TrashedItem {
    override val automatic: Boolean get() = false
}

/** Stored as JSON in the trash table; field names are persisted, never rename them. */
@Serializable
private data class LayoutSnapshot(
    val id: Long,
    val name: String,
    val spec: String,
    val createdAt: Long,
    val alarmIds: List<Long>,
    val wasDefault: Boolean,
)

/** Stored as JSON in the trash table; field names are persisted, never rename them. */
@Serializable
private data class GroupSnapshot(
    val group: AlarmGroupEntity,
    val alarms: List<AlarmEntity>,
    val memberIds: List<Long>,
)

/**
 * The trash (docs/PLANO.md 3.10): the last [MAX_ITEMS] deletions, each kept for [RETENTION].
 * Groups deleted with their alarms stay together and can be restored with any of them.
 */
@Singleton
class TrashRepository @Inject constructor(
    private val trashDao: TrashDao,
    private val alarmDao: AlarmDao,
    private val groupDao: AlarmGroupDao,
    private val clock: Clock,
) {
    fun observe(): Flow<List<TrashedItem>> = trashDao.observeAll().map { rows -> rows.mapNotNull { it.toItem() } }

    suspend fun get(trashId: Long): TrashedItem? = trashDao.get(trashId)?.toItem()

    /** Every alarm held in the trash, including those inside deleted groups. */
    suspend fun allAlarms(): List<Alarm> = trashDao.getAll().mapNotNull { it.toItem() }.flatMap { item ->
        when (item) {
            is TrashedAlarm -> listOf(item.alarm)
            is TrashedGroup -> item.alarms
            is TrashedLayout -> emptyList()
        }
    }

    /** Layouts held in the trash (their photos and videos must be kept). */
    suspend fun allLayouts(): List<LayoutEntity> = trashDao.getAll().mapNotNull { it.toItem() as? TrashedLayout }.map { it.layout }

    /** Keeps a deleted layout; the caller removes it from the library. Returns the trash id. */
    suspend fun moveLayoutToTrash(layout: LayoutEntity, alarmIds: List<Long>, wasDefault: Boolean): Long {
        val snapshot = LayoutSnapshot(layout.id, layout.name, layout.spec, layout.createdAt, alarmIds, wasDefault)
        val id = trashDao.insert(
            TrashEntity(
                type = TYPE_LAYOUT,
                payload = json.encodeToString(LayoutSnapshot.serializer(), snapshot),
                deletedAt = clock.millis(),
                automatic = false,
            ),
        )
        prune()
        return id
    }

    /** Takes a layout out of the trash (the caller puts it back in the library). */
    suspend fun takeLayout(trashId: Long): TrashedLayout? {
        val item = get(trashId) as? TrashedLayout ?: return null
        trashDao.delete(trashId)
        return item
    }

    /** Removes [alarm] from the alarm list and keeps a snapshot. Returns the trash id. */
    suspend fun moveToTrash(alarm: Alarm, automatic: Boolean): Long {
        val id = trashDao.insert(
            TrashEntity(
                type = TYPE_ALARM,
                payload = json.encodeToString(AlarmEntity.serializer(), alarm.toEntity()),
                deletedAt = clock.millis(),
                automatic = automatic,
            ),
        )
        alarmDao.delete(alarm.id)
        prune()
        return id
    }

    /**
     * Removes [group]. With [withAlarms] its alarms go to the trash inside it; otherwise they stay
     * in the list without a group. Returns the trash id.
     */
    suspend fun moveGroupToTrash(group: AlarmGroup, withAlarms: Boolean): Long {
        val members = alarmDao.inGroup(group.id)
        val snapshot = GroupSnapshot(
            group = group.toEntity(),
            alarms = if (withAlarms) members else emptyList(),
            memberIds = if (withAlarms) emptyList() else members.map { it.id },
        )
        val id = trashDao.insert(
            TrashEntity(
                type = TYPE_GROUP,
                payload = json.encodeToString(GroupSnapshot.serializer(), snapshot),
                deletedAt = clock.millis(),
                automatic = false,
            ),
        )
        if (withAlarms) members.forEach { alarmDao.delete(it.id) } else alarmDao.ungroup(group.id)
        groupDao.delete(group.id)
        prune()
        return id
    }

    /** Puts an alarm back in the list and removes it from the trash. Returns the restored alarm. */
    suspend fun restoreAlarm(trashId: Long, transform: (Alarm) -> Alarm): Alarm? {
        val item = get(trashId) as? TrashedAlarm ?: return null
        val restored = insertAlarm(transform(item.alarm))
        trashDao.delete(trashId)
        return restored
    }

    /**
     * Recreates a deleted group with the chosen [alarmIds] (all by default). Former members that
     * were kept as loose alarms rejoin it. Returns the restored alarms.
     */
    suspend fun restoreGroup(trashId: Long, alarmIds: Set<Long>?, transform: (Alarm) -> Alarm): List<Alarm>? {
        val item = get(trashId) as? TrashedGroup ?: return null
        // Reuse the original id when free (insert returns it), otherwise let Room assign one.
        val group = item.group.copy(id = if (groupDao.get(item.group.id) == null) item.group.id else 0)
        val groupId = groupDao.insert(group.toEntity())
        val restored = item.alarms
            .filter { alarmIds == null || it.id in alarmIds }
            .map { insertAlarm(transform(it).copy(groupId = groupId)) }
        item.memberIds.forEach { id -> alarmDao.get(id)?.let { alarmDao.update(it.copy(groupId = groupId)) } }
        trashDao.delete(trashId)
        // Alarms not chosen stay in the trash on their own, with the original deletion time.
        item.alarms.filter { alarmIds != null && it.id !in alarmIds }.forEach { alarm ->
            trashDao.insert(
                TrashEntity(
                    type = TYPE_ALARM,
                    payload = json.encodeToString(AlarmEntity.serializer(), alarm.copy(groupId = null).toEntity()),
                    deletedAt = item.deletedAt.toEpochMilli(),
                    automatic = false,
                ),
            )
        }
        return restored
    }

    suspend fun delete(trashId: Long) = trashDao.delete(trashId)

    suspend fun empty() = trashDao.deleteAll()

    /** Drops items older than the retention period and keeps at most [MAX_ITEMS]. */
    suspend fun prune() {
        trashDao.deleteOlderThan(clock.instant().minus(RETENTION).toEpochMilli())
        trashDao.keepNewest(MAX_ITEMS)
    }

    /** Keeps the original id when it is still free, so notifications and intents stay valid. */
    private suspend fun insertAlarm(alarm: Alarm): Alarm =
        if (alarmDao.get(alarm.id) == null) {
            alarmDao.insert(alarm.toEntity())
            alarm
        } else {
            alarm.copy(id = alarmDao.insert(alarm.copy(id = 0).toEntity()))
        }

    private fun TrashEntity.toItem(): TrashedItem? = runCatching {
        when (type) {
            TYPE_ALARM -> TrashedAlarm(
                id,
                json.decodeFromString(AlarmEntity.serializer(), payload).toAlarm(),
                Instant.ofEpochMilli(deletedAt),
                automatic,
            )
            TYPE_GROUP -> json.decodeFromString(GroupSnapshot.serializer(), payload).let { snapshot ->
                TrashedGroup(
                    id,
                    snapshot.group.toGroup(),
                    snapshot.alarms.map { it.toAlarm() },
                    snapshot.memberIds,
                    Instant.ofEpochMilli(deletedAt),
                )
            }
            TYPE_LAYOUT -> json.decodeFromString(LayoutSnapshot.serializer(), payload).let { snapshot ->
                TrashedLayout(
                    id,
                    LayoutEntity(snapshot.id, snapshot.name, snapshot.spec, snapshot.createdAt),
                    snapshot.alarmIds,
                    snapshot.wasDefault,
                    Instant.ofEpochMilli(deletedAt),
                )
            }
            else -> null
        }
    }.getOrNull()

    companion object {
        const val MAX_ITEMS = 50
        val RETENTION: Duration = Duration.ofDays(30)
        private const val TYPE_ALARM = "ALARM"
        private const val TYPE_GROUP = "GROUP"
        private const val TYPE_LAYOUT = "LAYOUT"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
