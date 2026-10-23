package io.github.imlemos.clock.alarm.ringing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The snooze dial's geometry (docs/PLANO.md 3.3): choices on a half circle around the Snooze
 * button, shortest on the left, longest on the right, opening upwards (or downwards when the
 * button sits near the top of the screen).
 */
object SnoozeDialMath {

    /**
     * Angle of each choice in degrees: 90 is straight up (or down); the ends sit [END_LIFT] above
     * the baseline so they clear the button's sides.
     */
    fun angles(count: Int): List<Float> =
        if (count == 1) listOf(90f) else List(count) { 180f - END_LIFT - it * (180f - 2 * END_LIFT) / (count - 1) }

    private const val END_LIFT = 15f

    /**
     * The choice under a finger at ([dx], [dy]) pixels from the centre (screen coordinates, y
     * down), or `null` inside [deadZone], where letting go cancels. Below the arc's baseline the
     * nearest end is chosen.
     */
    fun choiceAt(dx: Float, dy: Float, count: Int, deadZone: Float, up: Boolean): Int? {
        if (count <= 0 || hypot(dx, dy) < deadZone) return null
        var angle = Math.toDegrees(atan2((if (up) -dy else dy).toDouble(), dx.toDouble())).toFloat()
        if (angle < 0f) angle = if (dx >= 0f) 0f else 180f
        return angles(count).withIndex().minBy { abs(it.value - angle) }.index
    }
}
