package io.github.imlemos.clock.alarm.layout

/** Size of an area or element, in pixels. */
data class Size2(val width: Float, val height: Float)

/** Top-left corner of an element, in pixels from the area's top-left. */
data class Point2(val x: Float, val y: Float)

/**
 * Placement maths for the alarm screen (docs/PLANO.md 3.7): elements are positioned by their
 * centre as a fraction of the area and are always kept entirely inside it, on any screen.
 */
object LayoutGeometry {
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.5f

    /** Controls never shrink below their minimum touch size nor grow past this. */
    const val CONTROLS_MIN_SCALE = 1f
    const val CONTROLS_MAX_SCALE = 1.3f

    /**
     * The scale to draw an element at: [requested] limited to [min]..[max] and to what fits in
     * [area] (an element larger than the screen is shrunk, never cut off), but never below [min]
     * for controls, whose minimum size matters more.
     */
    fun effectiveScale(requested: Float, base: Size2, area: Size2, min: Float = MIN_SCALE, max: Float = MAX_SCALE): Float {
        val fits = if (base.width <= 0f || base.height <= 0f) max else minOf(area.width / base.width, area.height / base.height)
        return requested.coerceIn(min, max).coerceAtMost(fits).coerceAtLeast(min.coerceAtMost(fits))
    }

    /** Top-left of an element of [size] centred at [placement], moved inside [area] if needed. */
    fun topLeft(placement: Placement, size: Size2, area: Size2): Point2 {
        val x = (placement.x * area.width - size.width / 2f).coerceIn(0f, (area.width - size.width).coerceAtLeast(0f))
        val y = (placement.y * area.height - size.height / 2f).coerceIn(0f, (area.height - size.height).coerceAtLeast(0f))
        return Point2(x, y)
    }

    /** [placement] dragged by ([dx], [dy]) pixels, with its centre kept where the element fits. */
    fun moved(placement: Placement, dx: Float, dy: Float, size: Size2, area: Size2): Placement {
        if (area.width <= 0f || area.height <= 0f) return placement
        val halfW = (size.width / 2f / area.width).coerceAtMost(0.5f)
        val halfH = (size.height / 2f / area.height).coerceAtMost(0.5f)
        return placement.copy(
            x = (placement.x + dx / area.width).coerceIn(halfW, 1f - halfW),
            y = (placement.y + dy / area.height).coerceIn(halfH, 1f - halfH),
        )
    }
}
