package io.github.imlemos.clock.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarm ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long
}
