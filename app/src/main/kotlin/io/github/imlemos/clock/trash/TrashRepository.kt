package io.github.imlemos.clock.trash

import io.github.imlemos.clock.alarm.data.toAlarm
import io.github.imlemos.clock.alarm.data.toEntity
import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.data.db.AlarmDao
import io.github.imlemos.clock.core.data.db.AlarmEntity
import io.github.imlemos.clock.core.data.db.TrashDao
import io.github.imlemos.clock.core.data.db.TrashEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class TrashedAlarm(val trashId: Long, val alarm: Alarm, val deletedAt: Instant, val automatic: Boolean)

/**
 * The trash (docs/PLANO.md 3.10): the last [MAX_ITEMS] deletions, each kept for [RETENTION].
 * Groups join in phase 4.
 */
@Singleton
class TrashRepository @Inject constructor(
    private val trashDao: TrashDao,
    private val alarmDao: AlarmDao,
    private val clock: Clock,
) {
    fun observeAlarms(): Flow<List<TrashedAlarm>> = trashDao.observeAll().map { rows -> rows.mapNotNull { it.toTrashedAlarm() } }

    suspend fun get(trashId: Long): TrashedAlarm? = trashDao.get(trashId)?.toTrashedAlarm()

    suspend fun allAlarms(): List<Alarm> = trashDao.getAll().mapNotNull { it.toTrashedAlarm()?.alarm }

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

    /** Puts the alarm back in the list and removes it from the trash. Returns the restored alarm. */
    suspend fun restore(trashId: Long, transform: (Alarm) -> Alarm): Alarm? {
        val item = get(trashId) ?: return null
        val alarm = transform(item.alarm)
        // Keep the original id when it is still free, so notifications and intents stay valid.
        val restored = if (alarmDao.get(alarm.id) == null) {
            alarmDao.insert(alarm.toEntity())
            alarm
        } else {
            alarm.copy(id = alarmDao.insert(alarm.copy(id = 0).toEntity()))
        }
        trashDao.delete(trashId)
        return restored
    }

    suspend fun delete(trashId: Long) = trashDao.delete(trashId)

    suspend fun empty() = trashDao.deleteAll()

    /** Drops items older than the retention period and keeps at most [MAX_ITEMS]. */
    suspend fun prune() {
        trashDao.deleteOlderThan(clock.instant().minus(RETENTION).toEpochMilli())
        trashDao.keepNewest(MAX_ITEMS)
    }

    private fun TrashEntity.toTrashedAlarm(): TrashedAlarm? =
        if (type != TYPE_ALARM) {
            null
        } else {
            runCatching { json.decodeFromString(AlarmEntity.serializer(), payload).toAlarm() }
                .getOrNull()
                ?.let { TrashedAlarm(id, it, Instant.ofEpochMilli(deletedAt), automatic) }
        }

    companion object {
        const val MAX_ITEMS = 50
        val RETENTION: Duration = Duration.ofDays(30)
        private const val TYPE_ALARM = "ALARM"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
