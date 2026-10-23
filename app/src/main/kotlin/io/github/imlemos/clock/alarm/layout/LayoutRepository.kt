package io.github.imlemos.clock.alarm.layout

import io.github.imlemos.clock.core.data.db.AlarmDao
import io.github.imlemos.clock.core.data.db.LayoutDao
import io.github.imlemos.clock.core.data.db.LayoutEntity
import io.github.imlemos.clock.core.data.prefs.SettingsRepository
import io.github.imlemos.clock.trash.TrashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The layout library (docs/PLANO.md 3.7): built-in presets plus the user's layouts. Preset names
 * are resources, so presets come back with an empty [AlarmLayout.name] for the UI to fill in.
 */
@Singleton
class LayoutRepository @Inject constructor(
    private val layoutDao: LayoutDao,
    private val alarmDao: AlarmDao,
    private val settings: SettingsRepository,
    private val backgrounds: BackgroundStore,
    private val trash: TrashRepository,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<AlarmLayout>> = layoutDao.observeAll().map { rows -> presets() + rows.map { it.toLayout() } }

    suspend fun get(id: Long): AlarmLayout? =
        PresetLayouts.specs[id]?.let { AlarmLayout(id, "", it) } ?: layoutDao.get(id)?.toLayout()

    /** The id alarms without their own layout use. */
    suspend fun defaultId(): Long = settings.defaultLayoutId.first()?.takeIf { exists(it) } ?: PresetLayouts.DEFAULT

    /** What an alarm with [layoutId] shows: its layout, else the default, else the app's preset. */
    suspend fun resolve(layoutId: Long?): LayoutSpec =
        layoutId?.let { get(it)?.spec } ?: get(defaultId())?.spec ?: PresetLayouts.specs.getValue(PresetLayouts.DEFAULT)

    /** Adds a new layout (a draft saved for the first time). Returns its id. */
    suspend fun create(name: String, spec: LayoutSpec): Long {
        val id = layoutDao.insert(LayoutEntity(name = name, spec = LayoutJson.encode(spec), createdAt = clock.millis()))
        cleanBackgrounds()
        return id
    }

    /** Saves a user layout; presets are read-only. */
    suspend fun save(id: Long, name: String, spec: LayoutSpec) {
        val current = layoutDao.get(id) ?: return
        layoutDao.update(current.copy(name = name, spec = LayoutJson.encode(spec)))
        cleanBackgrounds()
    }

    suspend fun rename(id: Long, name: String) {
        val current = layoutDao.get(id) ?: return
        layoutDao.update(current.copy(name = name))
    }

    /**
     * Moves a user layout to the trash; alarms using it, and the default if it was this one, fall
     * back until it is restored.
     */
    suspend fun delete(id: Long) {
        val layout = layoutDao.get(id) ?: return
        val wasDefault = settings.defaultLayoutId.first() == id
        trash.moveLayoutToTrash(layout, alarmDao.idsWithLayout(id), wasDefault)
        layoutDao.delete(id)
        alarmDao.clearLayout(id)
        if (wasDefault) settings.setDefaultLayoutId(null)
    }

    /**
     * Brings a layout back from the trash, with the same id when it is free. Alarms that used it
     * and still have no layout of their own, and the default if still unchanged, use it again.
     */
    suspend fun restore(trashId: Long) {
        val item = trash.takeLayout(trashId) ?: return
        val original = item.layout
        val id = if (layoutDao.get(original.id) == null) {
            layoutDao.insert(original)
        } else {
            layoutDao.insert(original.copy(id = 0))
        }
        item.alarmIds.forEach { alarmId ->
            alarmDao.get(alarmId)?.takeIf { it.layoutId == null }?.let { alarmDao.update(it.copy(layoutId = id)) }
        }
        if (item.wasDefault && settings.defaultLayoutId.first() == null) settings.setDefaultLayoutId(id)
    }

    /** Removes copied photos and videos no layout, in the library or the trash, refers to anymore. */
    suspend fun cleanBackgrounds() {
        val inUse = (layoutDao.getAll() + trash.allLayouts()).mapNotNullTo(mutableSetOf()) { row ->
            when (val background = LayoutJson.decode(row.spec).background) {
                is Background.Image -> background.path
                is Background.Video -> background.path
                is Background.Preset -> null
            }
        }
        backgrounds.deleteUnused(inUse)
    }

    private suspend fun exists(id: Long): Boolean = PresetLayouts.isPreset(id) || layoutDao.get(id) != null

    private fun presets(): List<AlarmLayout> = PresetLayouts.specs.map { (id, spec) -> AlarmLayout(id, "", spec) }

    private fun LayoutEntity.toLayout() = AlarmLayout(id, name, LayoutJson.decode(spec))
}
