package io.github.imlemos.clock.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's touch feedback, one name per kind of moment so it stays consistent. Short and only on
 * meaningful interactions; the system's "Touch feedback" setting decides whether any of it plays.
 */
@Stable
class Haptics internal constructor(private val feedback: HapticFeedback) {
    /** A switch or check turned on or off. */
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** One of several options chosen: a chip, a segment, a tab, a day. */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Something done: saved, started, snoozed, dismissed. */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something removed: delete, discard, stop. */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A long press that opened something (selection mode, a dial). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** A drag that has just started moving something. */
    fun dragStart() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
