package io.github.imlemos.clock.alarm.layout

/** Size of an area or element, in pixels. */
data class Size2(val width: Float, val height: Float)

/** Top-left corner of an element, in pixels from the area's top-left. */
data class Point2(val x: Float, val y: Float)

/** An element's box: top-left and size, in pixels. */
data class Box2(val x: Float, val y: Float, val width: Float, val height: Float) {
    val bottom: Float get() = y + height

    fun overlapsHorizontally(other: Box2): Boolean = x < other.x + other.width && other.x < x + width
}

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

    const val MAX_BACKGROUND_ZOOM = 4f

    /**
     * Keeps a photo or video of [mediaAspect] (width / height) covering the whole [area]: zoom from
     * 1 (just filling it, centre crop) to [MAX_BACKGROUND_ZOOM], and offsets no larger than the part
     * that overflows on each side. A wide photo on a tall screen can move sideways even unzoomed.
     */
    fun clampFrame(frame: Frame, area: Size2, mediaAspect: Float): Frame {
        val zoom = frame.zoom.coerceIn(1f, MAX_BACKGROUND_ZOOM)
        if (area.width <= 0f || area.height <= 0f || mediaAspect <= 0f) return Frame(zoom)
        val coverWidth = maxOf(area.width, area.height * mediaAspect) * zoom
        val coverHeight = maxOf(area.height, area.width / mediaAspect) * zoom
        val limitX = (coverWidth - area.width) / 2f / area.width
        val limitY = (coverHeight - area.height) / 2f / area.height
        // "+ 0f" turns -0.0 into 0.0, so an unmoved frame equals the default one.
        return Frame(zoom, frame.offsetX.coerceIn(-limitX, limitX) + 0f, frame.offsetY.coerceIn(-limitY, limitY) + 0f)
    }

    /** [frame] after a drag of ([dx], [dy]) pixels and a pinch of [zoom]; see [clampFrame]. */
    fun transformFrame(frame: Frame, dx: Float, dy: Float, zoom: Float, area: Size2, mediaAspect: Float): Frame {
        if (area.width <= 0f || area.height <= 0f) return frame
        return clampFrame(
            Frame(frame.zoom * zoom, frame.offsetX + dx / area.width, frame.offsetY + dy / area.height),
            area,
            mediaAspect,
        )
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

    /**
     * Moves boxes down so none overlaps another (with [gap] between them), top to bottom. Small or
     * short screens and large fonts can make relative positions collide; text must stay readable.
     * A box pushed past the bottom stays at the bottom edge.
     */
    fun separate(boxes: List<Box2>, area: Size2, gap: Float): List<Box2> {
        val order = boxes.indices.sortedBy { boxes[it].y + boxes[it].height / 2f }
        val placed = arrayOfNulls<Box2>(boxes.size)
        for (index in order) {
            var box = boxes[index]
            var moved = true
            while (moved) {
                moved = false
                for (other in placed.filterNotNull()) {
                    if (box.overlapsHorizontally(other) && box.y < other.bottom + gap && other.y < box.bottom + gap) {
                        box = box.copy(y = other.bottom + gap)
                        moved = true
                    }
                }
                if (box.bottom > area.height) {
                    box = box.copy(y = (area.height - box.height).coerceAtLeast(0f))
                    break
                }
            }
            placed[index] = box
        }
        return placed.map { it!! }
    }
}
