package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE id = :id")
    fun observe(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM alarm WHERE groupId = :groupId")
    suspend fun inGroup(groupId: Long): List<AlarmEntity>

    @Query("UPDATE alarm SET groupId = NULL WHERE groupId = :groupId")
    suspend fun ungroup(groupId: Long)

    @Query("SELECT id FROM alarm WHERE layoutId = :layoutId")
    suspend fun idsWithLayout(layoutId: Long): List<Long>

    /** Alarms using a deleted layout go back to the default one. */
    @Query("UPDATE alarm SET layoutId = NULL WHERE layoutId = :layoutId")
    suspend fun clearLayout(layoutId: Long)
}
