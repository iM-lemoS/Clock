package io.github.imlemos.clock.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.GradientDirection
import io.github.imlemos.clock.alarm.layout.render.spec
import io.github.imlemos.clock.core.designsystem.theme.AccentPalette
import io.github.imlemos.clock.core.designsystem.theme.clockColors
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * What a widget shows, already formatted: the big time and the lines under it.
 *
 * @property warning A line above the time in the alert colour ("Alarms may not ring").
 * @property action The pill button at the bottom ("Skip next"); its tap area is placed by the
 *   widget layout over [WidgetImage.actionHeightPx].
 * @property timeMuted Draws the time in the muted colour (no alarm to show).
 */
data class WidgetContent(
    val hours: String,
    val minutes: String,
    val dayPeriod: String? = null,
    val lines: List<String> = emptyList(),
    val warning: String? = null,
    val action: String? = null,
    val timeMuted: Boolean = false,
)

/** A rendered widget and the height of the strip at its bottom that holds the action button. */
class WidgetImage(val bitmap: Bitmap, val actionHeightPx: Int)

/**
 * Draws widgets with the app's own fonts. Launchers cannot use them (widgets are inflated in a
 * restricted context that ignores app fonts), so the whole widget is an image (docs/PLANO.md 5.2).
 */
class WidgetRenderer(private val context: Context, private val photoFile: (String) -> File) {

    private data class Palette(val content: Int, val muted: Int, val surface: Int, val accent: Int)

    // The editor's preview and the minute updates may draw at the same time.
    private val fonts = ConcurrentHashMap<ClockFont, Typeface>()
    private val photos = LruCache<String, Bitmap>(2)

    /**
     * Renders [content] as [config] says at [widthPx] x [heightPx], for a light or dark ([night])
     * home screen. [accent] is the app's accent colour.
     */
    fun render(
        config: WidgetConfig,
        content: WidgetContent,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        night: Boolean,
        accent: Int,
    ): WidgetImage {
        val width = widthPx.coerceAtLeast(1)
        val height = heightPx.coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val palette = clockColors(night, Color(accent)).let {
            Palette(it.content.toArgb(), it.contentMuted.toArgb(), it.surface.toArgb(), it.accent.toArgb())
        }
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val radius = config.background.cornerRadius * density
        canvas.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        drawBackground(canvas, config.background, bounds, palette)

        val padding = config.padding * density
        val inner = RectF(bounds.left + padding, bounds.top + padding, bounds.right - padding, bounds.bottom - padding)
        val details = detailsPaint(config.details)
        val lineCount = content.lines.size + if (content.warning != null) 1 else 0
        // Lines and the button may take at most part of the height, so the time always shows.
        val detailSize = WidgetLayoutMath.detailSize(
            inner.width(), inner.height(), MIN_DETAIL_SP * density, MAX_DETAIL_SP * density, config.details.scale,
        ).coerceAtMost(WidgetLayoutMath.maxDetailSize(inner.height(), lineCount, hasAction = content.action != null))

        // The action button sits at the bottom, outside the centred stack.
        var actionHeight = 0f
        content.action?.let { label ->
            actionHeight = drawAction(canvas, label, details, detailSize, inner, config, palette)
        }
        val stackBottom = inner.bottom - actionHeight

        // Detail lines: each shrinks on its own if it is too wide.
        val lines = buildList {
            content.warning?.let { add(Line(it, ALERT)) }
            content.lines.forEach { add(Line(it, config.details.color.resolve(palette))) }
        }
        val lineSizes = lines.map { line ->
            details.textSize = detailSize
            WidgetLayoutMath.lineSize(detailSize, details.measureText(line.text), inner.width())
        }
        val detailsHeight = WidgetLayoutMath.detailsHeight(lines.size, detailSize)

        // The time takes what is left.
        val time = TimeBlock(config, content)
        val timeSize = WidgetLayoutMath.fitSize(
            time.width, time.height, inner.width(), (stackBottom - inner.top - detailsHeight).coerceAtLeast(0f),
        ) * config.time.scale.coerceIn(WidgetConfig.MIN_TIME_SCALE, 1f)
        val timeHeight = time.height * timeSize / WidgetLayoutMath.REFERENCE_SIZE
        val total = timeHeight + detailsHeight
        var y = inner.top + ((stackBottom - inner.top) - total) / 2f

        // The warning goes above the time; the other lines below it.
        val (above, below) = lines.withIndex().partition { content.warning != null && it.index == 0 }
        above.forEach { (index, line) ->
            y = drawLine(canvas, line, lineSizes[index], details, inner, config.alignment, y)
            y += detailSize * WidgetLayoutMath.DETAIL_GAP
        }
        if (timeSize > 0f) time.draw(canvas, timeSize, inner, y, palette)
        y += timeHeight + if (below.isNotEmpty()) detailSize * WidgetLayoutMath.DETAIL_GAP else 0f
        below.forEach { (index, line) -> y = drawLine(canvas, line, lineSizes[index], details, inner, config.alignment, y) }
        return WidgetImage(bitmap, actionHeight.toInt())
    }

    private class Line(val text: String, val color: Int)

    /** Draws [line] with its top at [top]; returns where the next line starts. */
    private fun drawLine(canvas: Canvas, line: Line, size: Float, paint: Paint, inner: RectF, alignment: WidgetAlignment, top: Float): Float {
        paint.textSize = size
        paint.color = line.color
        val metrics = paint.fontMetrics
        val lineHeight = size * WidgetLayoutMath.DETAIL_LINE_HEIGHT
        val baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent
        val x = WidgetLayoutMath.alignedLeft(alignment, inner.left, inner.right, paint.measureText(line.text))
        canvas.drawText(line.text, x, baseline, paint)
        return top + lineHeight
    }

    /** The pill button at the bottom of [inner]; returns the height it takes, gap included. */
    private fun drawAction(canvas: Canvas, label: String, paint: Paint, size: Float, inner: RectF, config: WidgetConfig, palette: Palette): Float {
        paint.textSize = size
        val textWidth = paint.measureText(label)
        val pillHeight = size * WidgetLayoutMath.ACTION_HEIGHT
        val pillWidth = (textWidth + size * 2f).coerceAtMost(inner.width())
        val left = WidgetLayoutMath.alignedLeft(config.alignment, inner.left, inner.right, pillWidth)
        val pill = RectF(left, inner.bottom - pillHeight, left + pillWidth, inner.bottom)
        val textColor = config.details.color.resolve(palette)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(config.time.color.resolve(palette), PILL_ALPHA) }
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, fill)
        paint.color = textColor
        val metrics = paint.fontMetrics
        val baseline = pill.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, pill.centerX() - textWidth / 2f, baseline, paint)
        return pillHeight + size * WidgetLayoutMath.DETAIL_GAP
    }

    /**
     * The time, measured at [WidgetLayoutMath.REFERENCE_SIZE]: inline (hours, separator,
     * minutes, day period) or stacked (hours over minutes).
     */
    private inner class TimeBlock(private val config: WidgetConfig, original: WidgetContent) {
        private val style = config.time

        // Stacked hours always take two digits ("07" over "30"), like the minutes below them.
        private val content = if (style.arrangement == TimeArrangement.STACKED && original.hours.length == 1) {
            original.copy(hours = "0" + original.hours)
        } else {
            original
        }
        private val paint = timePaint(style)
        private val periodPaint = detailsPaint(config.details)
        private val stacked = style.arrangement == TimeArrangement.STACKED
        private val digitHeight: Float
        private val hoursWidth: Float
        private val minutesWidth: Float
        private val separatorWidth: Float
        private val periodWidth: Float
        val width: Float
        val height: Float

        init {
            paint.textSize = WidgetLayoutMath.REFERENCE_SIZE
            periodPaint.textSize = WidgetLayoutMath.REFERENCE_SIZE * PERIOD_SIZE
            val bounds = Rect()
            paint.getTextBounds(DIGITS, 0, DIGITS.length, bounds)
            digitHeight = -bounds.top.toFloat()
            hoursWidth = paint.measureText(content.hours)
            minutesWidth = paint.measureText(content.minutes)
            separatorWidth = when {
                stacked -> 0f
                style.separator == SeparatorStyle.DOTS -> WidgetLayoutMath.REFERENCE_SIZE * DOTS_WIDTH
                style.separator == SeparatorStyle.GLYPH -> paint.measureText(":")
                else -> WidgetLayoutMath.REFERENCE_SIZE * GAP_WIDTH
            }
            periodWidth = content.dayPeriod?.let { periodPaint.measureText(it) + WidgetLayoutMath.REFERENCE_SIZE * PERIOD_GAP } ?: 0f
            width = if (stacked) max(hoursWidth, minutesWidth + periodWidth) else hoursWidth + separatorWidth + minutesWidth + periodWidth
            height = if (stacked) digitHeight * 2f + WidgetLayoutMath.REFERENCE_SIZE * STACK_GAP else digitHeight
        }

        fun draw(canvas: Canvas, size: Float, inner: RectF, top: Float, palette: Palette) {
            val k = size / WidgetLayoutMath.REFERENCE_SIZE
            paint.textSize = size
            periodPaint.textSize = size * PERIOD_SIZE
            val color = if (content.timeMuted) palette.muted else style.color.resolve(palette)
            paint.color = color
            periodPaint.color = color
            if (stacked) {
                val gap = size * STACK_GAP
                val firstBaseline = top + digitHeight * k
                val secondBaseline = firstBaseline + gap + digitHeight * k
                canvas.drawText(content.hours, WidgetLayoutMath.alignedLeft(config.alignment, inner.left, inner.right, hoursWidth * k), firstBaseline, paint)
                val secondWidth = (minutesWidth + periodWidth) * k
                val x = WidgetLayoutMath.alignedLeft(config.alignment, inner.left, inner.right, secondWidth)
                canvas.drawText(content.minutes, x, secondBaseline, paint)
                content.dayPeriod?.let { canvas.drawText(it, x + (minutesWidth + WidgetLayoutMath.REFERENCE_SIZE * PERIOD_GAP) * k, secondBaseline, periodPaint) }
                return
            }
            val baseline = top + digitHeight * k
            var x = WidgetLayoutMath.alignedLeft(config.alignment, inner.left, inner.right, width * k)
            canvas.drawText(content.hours, x, baseline, paint)
            x += hoursWidth * k
            val separatorColor = if (content.timeMuted) palette.muted else style.separatorColor.resolve(palette)
            when (style.separator) {
                SeparatorStyle.DOTS -> drawDots(canvas, x + separatorWidth * k / 2f, baseline - digitHeight * k / 2f, size, separatorColor)
                SeparatorStyle.GLYPH -> {
                    paint.color = separatorColor
                    canvas.drawText(":", x, baseline, paint)
                    paint.color = color
                }
                SeparatorStyle.NONE -> Unit
            }
            x += separatorWidth * k
            canvas.drawText(content.minutes, x, baseline, paint)
            x += minutesWidth * k
            content.dayPeriod?.let { canvas.drawText(it, x + WidgetLayoutMath.REFERENCE_SIZE * PERIOD_GAP * k, baseline, periodPaint) }
        }

        /** The app's drawn colon (see DotColon): two dots, rounder or squarer like Doto's. */
        private fun drawDots(canvas: Canvas, centerX: Float, centerY: Float, size: Float, color: Int) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val weight = effectiveWeight(style.font, style.weight)
            val radius = size * if (style.font == ClockFont.DOTO) DOT_RADIUS else DOT_RADIUS * (0.6f + 0.6f * weight / 900f)
            val corner = if (style.font == ClockFont.DOTO) radius * style.roundness / 100f else radius
            val gap = size * DOT_GAP
            listOf(centerY - gap, centerY + gap).forEach { y ->
                canvas.drawRoundRect(RectF(centerX - radius, y - radius, centerX + radius, y + radius), corner, corner, dotPaint)
            }
        }
    }

    private fun drawBackground(canvas: Canvas, background: WidgetBackground, bounds: RectF, palette: Palette) {
        val alpha = (background.opacity.coerceIn(0, 100) * 255 / 100)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (background.type) {
            WidgetBackgroundType.NONE -> Unit
            WidgetBackgroundType.SOLID -> {
                paint.color = background.color.resolve(palette)
                paint.alpha = paint.alpha * alpha / 255
                canvas.drawRect(bounds, paint)
            }
            WidgetBackgroundType.GRADIENT -> {
                val (x0, y0, x1, y1) = when (background.direction) {
                    GradientDirection.DOWN -> listOf(0f, 0f, 0f, bounds.bottom)
                    GradientDirection.ACROSS -> listOf(0f, 0f, bounds.right, 0f)
                    GradientDirection.DIAGONAL_DOWN -> listOf(0f, 0f, bounds.right, bounds.bottom)
                    GradientDirection.DIAGONAL_UP -> listOf(0f, bounds.bottom, bounds.right, 0f)
                }
                paint.shader = LinearGradient(x0, y0, x1, y1, background.color.resolve(palette), background.colorB.resolve(palette), Shader.TileMode.CLAMP)
                paint.alpha = alpha
                canvas.drawRect(bounds, paint)
            }
            WidgetBackgroundType.PHOTO -> {
                val photo = background.photo?.let(::loadPhoto)
                if (photo == null) {
                    paint.color = palette.surface
                    canvas.drawRect(bounds, paint)
                    return
                }
                // Centre-crop the photo to the widget.
                val scale = max(bounds.width() / photo.width, bounds.height() / photo.height)
                val w = photo.width * scale
                val h = photo.height * scale
                val dst = RectF((bounds.width() - w) / 2f, (bounds.height() - h) / 2f, (bounds.width() + w) / 2f, (bounds.height() + h) / 2f)
                paint.alpha = alpha
                canvas.drawBitmap(photo, null, dst, paint)
                val dim = background.dim.coerceIn(0, WidgetConfig.MAX_DIM)
                if (dim > 0) canvas.drawColor(withAlpha(0xFF000000.toInt(), dim * alpha / 100 / 255f))
            }
        }
    }

    private fun loadPhoto(name: String): Bitmap? =
        photos.get(name) ?: photoFile(name).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }?.also { photos.put(name, it) }

    private fun timePaint(style: WidgetTime) = fontPaint(style.font, effectiveWeight(style.font, style.weight), style.roundness)

    private fun detailsPaint(details: WidgetDetails) = fontPaint(details.font, effectiveWeight(details.font, details.weight), 100)

    private fun fontPaint(font: ClockFont, weight: Int, roundness: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = typeface(font)
        fontVariationSettings = buildString {
            append("'wght' $weight")
            if (font == ClockFont.DOTO) append(", 'ROND' $roundness")
        }
    }

    private fun typeface(font: ClockFont): Typeface =
        fonts.getOrPut(font) { ResourcesCompat.getFont(context, font.spec.res) ?: Typeface.DEFAULT }

    private fun WidgetColor.resolve(palette: Palette): Int = when (mode) {
        WidgetColor.Mode.CONTENT -> palette.content
        WidgetColor.Mode.MUTED -> palette.muted
        WidgetColor.Mode.SURFACE -> palette.surface
        WidgetColor.Mode.ACCENT -> palette.accent
        WidgetColor.Mode.CUSTOM -> argb
    }

    companion object {
        private const val DIGITS = "0123456789"
        private const val MIN_DETAIL_SP = 11f
        private const val MAX_DETAIL_SP = 22f
        private const val PERIOD_SIZE = 0.22f
        private const val PERIOD_GAP = 0.08f
        private const val STACK_GAP = 0.14f
        private const val GAP_WIDTH = 0.14f

        // Same proportions as DotColon.
        private const val DOTS_WIDTH = 0.34f
        private const val DOT_RADIUS = 0.055f
        private const val DOT_GAP = 0.16f
        private const val PILL_ALPHA = 0.14f
        private val ALERT = AccentPalette.Red.color.toArgb()

        /** The weight a font is drawn at: the chosen one, else its default, within its range. */
        fun effectiveWeight(font: ClockFont, weight: Int?): Int =
            (weight ?: font.spec.defaultWeight).coerceIn(font.spec.minWeight, font.spec.maxWeight)

        private fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0xFFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}
