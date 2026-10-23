package io.github.imlemos.clock.alarm.layout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the alarm screen looks like (docs/PLANO.md 3.7). Stored as JSON, so field and class names
 * are persisted: never rename them, only add fields with defaults.
 *
 * Positions are relative (the centre of each element as a fraction of the screen's safe area), so
 * a layout adapts to any screen size or proportion; [LayoutGeometry] keeps elements inside it.
 */
@Serializable
data class LayoutSpec(
    val background: Background = Background.Preset(PresetKind.DOTS),
    val clock: ClockElement = ClockElement(),
    val label: TextElement = TextElement(Placement(0.5f, 0.47f, 1f)),
    val date: TextElement = TextElement(Placement(0.5f, 0.36f, 1f)),
    val controls: ControlsElement = ControlsElement(),
)

/** Centre of an element as fractions of the area, and its size relative to the default. */
@Serializable
data class Placement(val x: Float, val y: Float, val scale: Float = 1f)

/**
 * How the clock is drawn. [DOTS] and [MONO] are from the first layouts and are read as [DIGITAL]
 * with the Doto or mono font (see [ClockElement.normalized]).
 */
@Serializable
enum class ClockStyle { DIGITAL, STACKED, ANALOG, ANALOG_LINES, DOTS, MONO }

/** Fonts for digital clocks; all bundled variable fonts (licences in licenses/fonts). */
@Serializable
enum class ClockFont { DOTO, MONO, OUTFIT, SERIF, ORBITRON, CONDENSED, ROUNDED }

/**
 * [color] is ARGB; `null` follows the theme's content colour. [weight] (100..900) is the font
 * weight, or the hands' thickness on analog clocks; `null` uses the font's own default.
 * [roundness] (0..100) only applies to the Doto dots.
 */
@Serializable
data class ClockElement(
    val placement: Placement = Placement(0.5f, 0.27f, 1f),
    val style: ClockStyle = ClockStyle.DIGITAL,
    val color: Int? = null,
    val font: ClockFont = ClockFont.DOTO,
    val weight: Int? = null,
    val roundness: Int = 100,
) {
    /** Maps the first layouts' styles onto the current ones. */
    fun normalized(): ClockElement = when (style) {
        ClockStyle.DOTS -> copy(style = ClockStyle.DIGITAL, font = ClockFont.DOTO)
        ClockStyle.MONO -> copy(style = ClockStyle.DIGITAL, font = ClockFont.MONO)
        else -> this
    }
}

/** [dateParts] only applies to the date element. */
@Serializable
data class TextElement(
    val placement: Placement,
    val color: Int? = null,
    val visible: Boolean = true,
    val dateParts: DateParts = DateParts(),
)

/**
 * Which parts of the date show and, when the weekday shows with other parts, whether it shares
 * their line or sits on its own line above or below them.
 */
@Serializable
data class DateParts(
    val weekday: Boolean = true,
    val day: Boolean = true,
    val month: Boolean = true,
    val year: Boolean = true,
    val arrangement: DateArrangement = DateArrangement.INLINE,
) {
    /** Day, month or year: what goes with the weekday. */
    val hasRest: Boolean get() = day || month || year

    /** The weekday on its own line only makes sense when there is something else to show. */
    val split: Boolean get() = weekday && hasRest && arrangement != DateArrangement.INLINE
}

@Serializable
enum class DateArrangement { INLINE, WEEKDAY_ABOVE, WEEKDAY_BELOW }

/** Which way a gradient background runs, from the first colour to the second. */
@Serializable
enum class GradientDirection { DOWN, ACROSS, DIAGONAL_DOWN, DIAGONAL_UP }

/** Snooze and the dismiss slider, kept together and always at least their minimum size. */
@Serializable
data class ControlsElement(val placement: Placement = Placement(0.5f, 0.86f, 1f))

@Serializable
enum class PresetKind { DOTS, WAVES, MATRIX, SOLID, GRADIENT }

@Serializable
sealed interface Background {
    /** Drawn in code. [colorA]/[colorB] are ARGB; `null` uses the preset's own colours. */
    @Serializable
    @SerialName("preset")
    data class Preset(
        val kind: PresetKind,
        val colorA: Int? = null,
        val colorB: Int? = null,
        /** Gradients only. */
        val direction: GradientDirection = GradientDirection.DOWN,
    ) : Background

    /**
     * A photo copied into app storage. [dim] darkens it so text stays readable (0..0.8); [frame]
     * is how it is zoomed and positioned.
     */
    @Serializable
    @SerialName("image")
    data class Image(val path: String, val dim: Float = 0.35f, val frame: Frame = Frame()) : Background

    /** A silent, looping video copied into app storage. */
    @Serializable
    @SerialName("video")
    data class Video(val path: String, val dim: Float = 0.35f, val frame: Frame = Frame()) : Background
}

/**
 * Zoom and position of a photo or video that already fills the screen (centre crop). Offsets are
 * fractions of the screen; [LayoutGeometry.clampFrame] keeps the screen always covered.
 */
@Serializable
data class Frame(val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

/** A layout in the library: one of the built-in [presets] (negative ids) or the user's own. */
data class AlarmLayout(val id: Long, val name: String, val spec: LayoutSpec, val builtIn: Boolean = id < 0)

object LayoutJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun encode(spec: LayoutSpec): String = json.encodeToString(LayoutSpec.serializer(), spec)

    /** Unreadable data falls back to the default layout rather than breaking the alarm screen. */
    fun decode(text: String): LayoutSpec = runCatching { json.decodeFromString(LayoutSpec.serializer(), text) }
        .getOrDefault(LayoutSpec())
        .let { it.copy(clock = it.clock.normalized()) }
}

/**
 * Layouts that ship with the app (docs/PLANO.md 3.7). Their ids are fixed and negative so alarms
 * can refer to them; names are resources, resolved by the UI from [PresetLayouts.nameRes].
 */
object PresetLayouts {
    const val DOTS = -1L
    const val WAVES = -2L
    const val MATRIX = -3L
    const val ANALOG = -4L
    const val TALL = -5L
    const val CLASSIC = -6L

    /** The app's default when the user has not chosen one. */
    const val DEFAULT = DOTS

    val specs: Map<Long, LayoutSpec> = linkedMapOf(
        DOTS to LayoutSpec(),
        WAVES to LayoutSpec(
            background = Background.Preset(PresetKind.WAVES),
            clock = ClockElement(Placement(0.5f, 0.3f, 1f), font = ClockFont.MONO, weight = 300),
            date = TextElement(Placement(0.5f, 0.4f, 1f)),
            label = TextElement(Placement(0.5f, 0.5f, 1.1f)),
        ),
        MATRIX to LayoutSpec(
            background = Background.Preset(PresetKind.MATRIX),
            clock = ClockElement(Placement(0.5f, 0.24f, 1.25f)),
            date = TextElement(Placement(0.5f, 0.36f, 1f)),
            label = TextElement(Placement(0.5f, 0.46f, 1f)),
        ),
        ANALOG to LayoutSpec(
            background = Background.Preset(PresetKind.GRADIENT),
            clock = ClockElement(Placement(0.5f, 0.3f, 1f), ClockStyle.ANALOG),
            date = TextElement(Placement(0.5f, 0.52f, 1f)),
            label = TextElement(Placement(0.5f, 0.6f, 1f)),
        ),
        TALL to LayoutSpec(
            background = Background.Preset(PresetKind.SOLID, colorA = 0xFF0B0B0B.toInt()),
            clock = ClockElement(Placement(0.3f, 0.3f, 1.6f), ClockStyle.STACKED, font = ClockFont.CONDENSED, weight = 800),
            date = TextElement(Placement(0.72f, 0.12f, 1f)),
            label = TextElement(Placement(0.5f, 0.66f, 1f)),
        ),
        CLASSIC to LayoutSpec(
            background = Background.Preset(PresetKind.GRADIENT, colorA = 0xFF2A1F14.toInt()),
            clock = ClockElement(Placement(0.5f, 0.28f, 1.1f), font = ClockFont.SERIF, weight = 500),
            date = TextElement(Placement(0.5f, 0.39f, 1f)),
            label = TextElement(Placement(0.5f, 0.5f, 1f)),
        ),
    )

    fun isPreset(id: Long): Boolean = id in specs
}
