package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/** A named group of alarms (docs/PLANO.md 3.8). Added in schema 4. */
@Serializable
@Entity(tableName = "alarm_group")
data class AlarmGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Master switch: off silences every alarm in the group without changing their own switches. */
    val enabled: Boolean = true,
    val expanded: Boolean = true,
    val createdAt: Long,
)
