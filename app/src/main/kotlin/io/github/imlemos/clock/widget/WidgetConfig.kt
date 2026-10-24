package io.github.imlemos.clock.widget

import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.DateParts
import io.github.imlemos.clock.alarm.layout.GradientDirection
import kotlinx.serialization.Serializable

/** The home screen widgets (docs/PLANO.md 5.2). */
enum class WidgetKind { CLOCK, NEXT_ALARM }

@Serializable
enum class TimeArrangement { INLINE, STACKED }

/** Between hours and minutes: the app's drawn dots, the font's own colon, or just a gap. */
@Serializable
enum class SeparatorStyle { DOTS, GLYPH, NONE }

@Serializable
enum class WidgetAlignment { START, CENTER, END }

@Serializable
enum class WidgetBackgroundType { NONE, SOLID, GRADIENT, PHOTO }

/**
 * A widget colour: one of the theme's (resolved separately for light and dark home screens), the
 * app's accent, or a fixed [argb].
 */
@Serializable
data class WidgetColor(val mode: Mode = Mode.CONTENT, val argb: Int = 0) {
    enum class Mode { CONTENT, MUTED, SURFACE, ACCENT, CUSTOM }

    /** Depends on whether the home screen is light or dark. */
    val themed: Boolean get() = mode == Mode.CONTENT || mode == Mode.MUTED || mode == Mode.SURFACE

    companion object {
        val Content = WidgetColor(Mode.CONTENT)
        val Muted = WidgetColor(Mode.MUTED)
        val Surface = WidgetColor(Mode.SURFACE)
        val Accent = WidgetColor(Mode.ACCENT)
        fun custom(argb: Int) = WidgetColor(Mode.CUSTOM, argb)
    }
}

/**
 * The big time. [weight] `null` uses the font's default; [roundness] only applies to the dot
 * font; [scale] shrinks it below the largest size that fits (0.4..1).
 */
@Serializable
data class WidgetTime(
    val font: ClockFont = ClockFont.DOTO,
    val weight: Int? = null,
    val roundness: Int = 100,
    val arrangement: TimeArrangement = TimeArrangement.INLINE,
    val separator: SeparatorStyle = SeparatorStyle.DOTS,
    val color: WidgetColor = WidgetColor.Content,
    val separatorColor: WidgetColor = WidgetColor.Content,
    val scale: Float = 1f,
)

/** The smaller lines under the time: date, day, label, countdown. [scale] is 0.6..1.6. */
@Serializable
data class WidgetDetails(
    val font: ClockFont = ClockFont.MONO,
    val weight: Int? = null,
    val color: WidgetColor = WidgetColor.Muted,
    val scale: Float = 1f,
)

/**
 * [opacity] and [dim] (darkens a photo) are percentages; [cornerRadius] is in dp. [photo] is the
 * file name of a copy in the app's storage.
 */
@Serializable
data class WidgetBackground(
    val type: WidgetBackgroundType = WidgetBackgroundType.SOLID,
    val color: WidgetColor = WidgetColor.Surface,
    val colorB: WidgetColor = WidgetColor.Accent,
    val direction: GradientDirection = GradientDirection.DOWN,
    val photo: String? = null,
    val opacity: Int = 100,
    val dim: Int = 30,
    val cornerRadius: Int = 28,
)

/**
 * Everything a widget looks like and shows. The next-alarm fields are ignored by the clock and
 * the date fields by the next-alarm widget.
 */
@Serializable
data class WidgetConfig(
    val time: WidgetTime = WidgetTime(),
    val details: WidgetDetails = WidgetDetails(),
    val background: WidgetBackground = WidgetBackground(),
    val alignment: WidgetAlignment = WidgetAlignment.CENTER,
    /** Inner margin in dp. */
    val padding: Int = 16,
    // Clock
    val showDate: Boolean = true,
    val date: DateParts = DateParts(year = false),
    // Next alarm
    val showDay: Boolean = true,
    val showLabel: Boolean = true,
    val showCountdown: Boolean = true,
    val showSkip: Boolean = true,
) {
    /** Some colour follows the theme, so light and dark home screens need different images. */
    val themed: Boolean
        get() = time.color.themed || time.separatorColor.themed || details.color.themed ||
            (background.type == WidgetBackgroundType.SOLID && background.color.themed) ||
            (background.type == WidgetBackgroundType.GRADIENT && (background.color.themed || background.colorB.themed))

    companion object {
        const val MIN_TIME_SCALE = 0.4f
        const val MIN_DETAILS_SCALE = 0.6f
        const val MAX_DETAILS_SCALE = 1.6f
        const val MAX_PADDING = 40
        const val MAX_RADIUS = 48
        const val MAX_DIM = 80
    }
}
