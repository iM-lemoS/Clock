package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Minimal alarm row for schema version 1. Phase 1 extends it with recurrence, sound and the
 * remaining fields from the data model in docs/PLANO.md.
 */
@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val createdAtEpochMillis: Long,
)
