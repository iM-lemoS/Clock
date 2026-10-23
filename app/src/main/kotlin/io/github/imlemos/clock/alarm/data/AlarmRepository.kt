package io.github.imlemos.clock.alarm.data

import io.github.imlemos.clock.alarm.model.Alarm
import io.github.imlemos.clock.core.data.db.AlarmDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepository @Inject constructor(private val dao: AlarmDao) {

    fun observeAll(): Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

    fun observe(id: Long): Flow<Alarm?> = dao.observe(id).map { it?.toAlarm() }

    suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toAlarm() }

    suspend fun get(id: Long): Alarm? = dao.get(id)?.toAlarm()

    /** Inserts a new alarm (id 0) or updates an existing one. Returns the alarm with its id. */
    suspend fun save(alarm: Alarm): Alarm =
        if (alarm.id == 0L) {
            alarm.copy(id = dao.insert(alarm.toEntity()))
        } else {
            dao.update(alarm.toEntity())
            alarm
        }

    suspend fun delete(id: Long) = dao.delete(id)
}
