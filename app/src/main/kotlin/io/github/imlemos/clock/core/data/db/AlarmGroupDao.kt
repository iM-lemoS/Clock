package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmGroupDao {
    @Query("SELECT * FROM alarm_group ORDER BY createdAt")
    fun observeAll(): Flow<List<AlarmGroupEntity>>

    @Query("SELECT * FROM alarm_group ORDER BY createdAt")
    suspend fun getAll(): List<AlarmGroupEntity>

    @Query("SELECT * FROM alarm_group WHERE id = :id")
    suspend fun get(id: Long): AlarmGroupEntity?

    @Insert
    suspend fun insert(group: AlarmGroupEntity): Long

    @Update
    suspend fun update(group: AlarmGroupEntity)

    @Query("DELETE FROM alarm_group WHERE id = :id")
    suspend fun delete(id: Long)
}
