package io.github.imlemos.clock.timer.data

import io.github.imlemos.clock.core.data.db.TimerDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerRepository @Inject constructor(private val dao: TimerDao) {
    fun observeAll(): Flow<List<Timer>> = dao.observeAll().map { rows -> rows.map { it.toTimer() } }

    suspend fun getAll(): List<Timer> = dao.getAll().map { it.toTimer() }

    suspend fun get(id: Long): Timer? = dao.get(id)?.toTimer()

    /** Inserts a new timer or updates an existing one; returns it with its id. */
    suspend fun save(timer: Timer): Timer =
        if (timer.id == 0L) timer.copy(id = dao.insert(timer.toEntity())) else timer.also { dao.update(it.toEntity()) }

    suspend fun delete(id: Long) = dao.delete(id)
}
