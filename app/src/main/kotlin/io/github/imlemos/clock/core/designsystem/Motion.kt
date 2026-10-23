package io.github.imlemos.clock.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app's motion: short and eased, cheap enough for slow phones (colours, alpha, offsets and
 * sizes only; nothing re-renders blur while it animates).
 */
object Motion {
    /** Selection changes: chips, segments, days. */
    const val FAST = 160

    /** Things appearing, moving or resizing: panels, sections. */
    const val MEDIUM = 240

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = EaseOutCubic)

    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = EaseOutCubic)
}

/** [target] reached with a short fade, for selection colours. */
@Composable
fun animatedColor(target: Color): Color = animateColorAsState(target, Motion.fast(), label = "color").value
