package io.github.imlemos.clock.widget

import kotlin.math.min

/**
 * Sizing rules for widget images, kept free of Android so they can be unit tested. Text widths
 * and heights scale linearly with the font size, so everything is measured once at
 * [REFERENCE_SIZE] and scaled.
 */
object WidgetLayoutMath {
    const val REFERENCE_SIZE = 100f

    /** Line height of the detail lines, relative to their font size. */
    const val DETAIL_LINE_HEIGHT = 1.3f

    /** Space between the time and the detail lines, relative to the detail font size. */
    const val DETAIL_GAP = 0.5f

    /**
     * The largest font size at which something measuring [width] x [height] at
     * [REFERENCE_SIZE] fits in [availableWidth] x [availableHeight]; never negative.
     */
    fun fitSize(width: Float, height: Float, availableWidth: Float, availableHeight: Float): Float {
        if (width <= 0f || height <= 0f || availableWidth <= 0f || availableHeight <= 0f) return 0f
        return REFERENCE_SIZE * min(availableWidth / width, availableHeight / height)
    }

    /**
     * Font size of the detail lines: about a ninth of the widget's shorter side, kept between
     * [minPx] and [maxPx], then scaled by the user's choice.
     */
    fun detailSize(innerWidth: Float, innerHeight: Float, minPx: Float, maxPx: Float, scale: Float): Float =
        (min(innerHeight, innerWidth * 0.5f) * 0.11f).coerceIn(minPx, maxPx) * scale

    /** A detail line shrinks on its own when it is wider than the widget. */
    fun lineSize(size: Float, widthAtSize: Float, availableWidth: Float): Float =
        if (widthAtSize <= availableWidth || widthAtSize <= 0f) size else size * availableWidth / widthAtSize

    /** Height taken by [lines] detail lines at [size], including the gap above them. */
    fun detailsHeight(lines: Int, size: Float): Float =
        if (lines == 0) 0f else size * (DETAIL_GAP + lines * DETAIL_LINE_HEIGHT)

    /** Left edge of something [width] wide in [left]..[right]. */
    fun alignedLeft(alignment: WidgetAlignment, left: Float, right: Float, width: Float): Float = when (alignment) {
        WidgetAlignment.START -> left
        WidgetAlignment.CENTER -> (left + right - width) / 2f
        WidgetAlignment.END -> right - width
    }
}
