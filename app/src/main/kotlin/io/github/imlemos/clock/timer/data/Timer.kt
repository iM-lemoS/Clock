package io.github.imlemos.clock.timer.data

import io.github.imlemos.clock.core.data.db.TimerEntity
import io.github.imlemos.clock.core.scheduling.TimerRun
import io.github.imlemos.clock.core.scheduling.TimerStatus
import java.time.Instant

/** A saved timer (docs/PLANO.md 4). [ringingSince] is set while it rings. */
data class Timer(
    val id: Long = 0,
    val name: String,
    val run: TimerRun,
    val ringingSince: Instant? = null,
    val createdAt: Instant,
) {
    val status: TimerStatus get() = run.status
}

fun TimerEntity.toTimer() = Timer(
    id = id,
    name = name,
    run = TimerRun(
        status = TimerStatus.entries.firstOrNull { it.name == status } ?: TimerStatus.IDLE,
        durationMillis = durationMillis,
        remainingMillis = remainingMillis,
        endElapsed = endElapsed,
        endWall = endWall,
    ),
    ringingSince = ringingSince?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Timer.toEntity() = TimerEntity(
    id = id,
    name = name,
    durationMillis = run.durationMillis,
    status = run.status.name,
    remainingMillis = run.remainingMillis,
    endElapsed = run.endElapsed,
    endWall = run.endWall,
    ringingSince = ringingSince?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
)
