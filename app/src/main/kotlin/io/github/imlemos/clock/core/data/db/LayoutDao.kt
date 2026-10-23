package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {
    @Query("SELECT * FROM alarm_layout ORDER BY createdAt")
    fun observeAll(): Flow<List<LayoutEntity>>

    @Query("SELECT * FROM alarm_layout ORDER BY createdAt")
    suspend fun getAll(): List<LayoutEntity>

    @Query("SELECT * FROM alarm_layout WHERE id = :id")
    suspend fun get(id: Long): LayoutEntity?

    @Insert
    suspend fun insert(layout: LayoutEntity): Long

    @Update
    suspend fun update(layout: LayoutEntity)

    @Query("DELETE FROM alarm_layout WHERE id = :id")
    suspend fun delete(id: Long)
}
