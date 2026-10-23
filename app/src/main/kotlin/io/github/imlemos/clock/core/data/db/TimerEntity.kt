package io.github.imlemos.clock.core.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A saved timer (docs/PLANO.md 4) and its countdown (TimerRun). [endElapsed] is in
 * `elapsedRealtime` milliseconds, [endWall] and [ringingSince] in epoch milliseconds. Added in
 * schema 6.
 */
@Entity(tableName = "timer")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val durationMillis: Long,
    val status: String,
    val remainingMillis: Long,
    val endElapsed: Long? = null,
    val endWall: Long? = null,
    val ringingSince: Long? = null,
    val createdAt: Long,
)
