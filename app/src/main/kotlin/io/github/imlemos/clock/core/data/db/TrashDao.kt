package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash")
    suspend fun getAll(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE id = :id")
    suspend fun get(id: Long): TrashEntity?

    @Insert
    suspend fun insert(item: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash")
    suspend fun deleteAll()

    @Query("DELETE FROM trash WHERE deletedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    /** Keeps only the [keep] most recent items. */
    @Query("DELETE FROM trash WHERE id NOT IN (SELECT id FROM trash ORDER BY deletedAt DESC LIMIT :keep)")
    suspend fun keepNewest(keep: Int)
}
