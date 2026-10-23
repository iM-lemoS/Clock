package io.github.imlemos.clock.core.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Alarm row. Instants are epoch milliseconds; the recurrence is flattened into
 * [recurrenceType] plus [weekDays] (bit 0 = Monday) or [dates] (comma-separated ISO dates).
 */
@Serializable
@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val recurrenceType: String,
    val weekDays: Int = 0,
    val dates: String = "",
    val label: String = "",
    val enabled: Boolean = true,
    val zoneId: String? = null,
    val skippedOccurrence: Long? = null,
    val lastTriggeredOccurrence: Long? = null,
    val snoozedUntil: Long? = null,
    val snoozeMinutes: Int,
    val ringDurationSeconds: Int,
    val vibrate: Boolean = true,
    val ringtoneUri: String? = null,
    val createdAt: Long,
    /** Goes to the trash once dismissed (docs/PLANO.md 3.1). Added in schema 2. */
    @ColumnInfo(defaultValue = "0") val singleUse: Boolean = false,
    /** When the alarm was last saved or switched on; earlier occurrences never count as missed. */
    @ColumnInfo(defaultValue = "0") val armedAt: Long = 0,
)
