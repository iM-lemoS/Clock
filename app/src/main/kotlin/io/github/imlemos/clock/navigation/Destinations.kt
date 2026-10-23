package io.github.imlemos.clock.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import io.github.imlemos.clock.R
import kotlinx.serialization.Serializable

/** Destinations shown in the floating navigation bar. */
@Serializable
sealed interface TopLevelDestination : NavKey {
    @get:DrawableRes val icon: Int
    @get:StringRes val label: Int

    /** Content description of the floating "+" on this tab, or `null` when the tab has none. */
    @get:StringRes val addAction: Int?
}

@Serializable
data object AlarmsDestination : TopLevelDestination {
    override val icon get() = R.drawable.ic_alarm
    override val label get() = R.string.nav_alarms
    override val addAction get() = R.string.action_add_alarm
}

@Serializable
data object WorldClockDestination : TopLevelDestination {
    override val icon get() = R.drawable.ic_world
    override val label get() = R.string.nav_world
    override val addAction get() = R.string.world_add_city
}

@Serializable
data object TimersDestination : TopLevelDestination {
    override val icon get() = R.drawable.ic_hourglass_empty
    override val label get() = R.string.nav_timers
    override val addAction get() = R.string.action_add_timer
}

@Serializable
data object StopwatchDestination : TopLevelDestination {
    override val icon get() = R.drawable.ic_timer
    override val label get() = R.string.nav_stopwatch
    override val addAction: Int? get() = null
}

@Serializable
data object SettingsDestination : NavKey

/** Alarm editor; [alarmId] 0 creates a new alarm, in [zoneId] when given (from the world clock). */
@Serializable
data class AlarmEditorDestination(val alarmId: Long, val zoneId: String? = null) : NavKey

@Serializable
data object ReliabilityDestination : NavKey

@Serializable
data object TrashDestination : NavKey

/** Batch edit of the selected alarms (docs/PLANO.md 3.9). */
@Serializable
data class BatchEditDestination(val alarmIds: List<Long>) : NavKey

/** Timer editor; [timerId] 0 creates a new timer. */
@Serializable
data class TimerEditorDestination(val timerId: Long) : NavKey

/** The layout library (docs/PLANO.md 3.7). */
@Serializable
data object LayoutsDestination : NavKey

/**
 * Edits one of the user's layouts or, with [newName], a new draft copied from [layoutId] that is
 * only created when saved. [requestId] lets the opener receive the new layout (LayoutDrafts).
 */
@Serializable
data class LayoutEditorDestination(val layoutId: Long, val newName: String? = null, val requestId: Long = 0) : NavKey

/** Debug builds only: compares the glass levels over busy content. */
@Serializable
data object GlassLabDestination : NavKey

val TopLevelDestinations: List<TopLevelDestination> =
    listOf(AlarmsDestination, WorldClockDestination, TimersDestination, StopwatchDestination)
