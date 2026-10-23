package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Alarm row. Instants are epoch milliseconds; the recurrence is flattened into
 * [recurrenceType] plus [weekDays] (bit 0 = Monday) or [dates] (comma-separated ISO dates).
 */
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
)
