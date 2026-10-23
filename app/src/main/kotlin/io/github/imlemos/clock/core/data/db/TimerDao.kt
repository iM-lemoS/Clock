package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query("SELECT * FROM timer ORDER BY createdAt")
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timer ORDER BY createdAt")
    suspend fun getAll(): List<TimerEntity>

    @Query("SELECT * FROM timer WHERE id = :id")
    suspend fun get(id: Long): TimerEntity?

    @Insert
    suspend fun insert(timer: TimerEntity): Long

    @Update
    suspend fun update(timer: TimerEntity)

    @Query("DELETE FROM timer WHERE id = :id")
    suspend fun delete(id: Long)
}
