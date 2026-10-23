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

/** Debug builds only: compares the glass levels over busy content. */
@Serializable
data object GlassLabDestination : NavKey

val TopLevelDestinations: List<TopLevelDestination> =
    listOf(AlarmsDestination, TimersDestination, StopwatchDestination)
