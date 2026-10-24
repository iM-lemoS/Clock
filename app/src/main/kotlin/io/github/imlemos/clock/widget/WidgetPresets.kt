package io.github.imlemos.clock.widget

import androidx.annotation.StringRes
import io.github.imlemos.clock.R
import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.GradientDirection

/**
 * Ready-made looks to start from. Applying one replaces the appearance and keeps what the widget
 * shows (date parts, day, label, countdown, skip button).
 */
enum class WidgetPreset(@param:StringRes val label: Int) {
    CARD(R.string.widget_preset_card),
    DOTS(R.string.widget_preset_dots),
    MINIMAL(R.string.widget_preset_minimal),
    BOLD(R.string.widget_preset_bold),
    ELEGANT(R.string.widget_preset_elegant),
    GLASS(R.string.widget_preset_glass),
    SUNSET(R.string.widget_preset_sunset),
    ;

    fun applyTo(config: WidgetConfig): WidgetConfig {
        val look = when (this) {
            CARD -> WidgetConfig()
            DOTS -> WidgetConfig(
                time = WidgetTime(separatorColor = WidgetColor.Accent),
                background = WidgetBackground(type = WidgetBackgroundType.NONE),
            )
            MINIMAL -> WidgetConfig(
                time = WidgetTime(font = ClockFont.MONO, weight = 200, separator = SeparatorStyle.GLYPH),
                details = WidgetDetails(weight = 300),
                background = WidgetBackground(type = WidgetBackgroundType.NONE),
                alignment = WidgetAlignment.START,
            )
            BOLD -> WidgetConfig(
                time = WidgetTime(font = ClockFont.CONDENSED, weight = 900, arrangement = TimeArrangement.STACKED, color = WidgetColor.Accent),
                details = WidgetDetails(font = ClockFont.CONDENSED, weight = 700, color = WidgetColor.Content),
                alignment = WidgetAlignment.START,
            )
            ELEGANT -> WidgetConfig(
                time = WidgetTime(font = ClockFont.SERIF, weight = 400, separator = SeparatorStyle.GLYPH),
                details = WidgetDetails(font = ClockFont.SERIF, weight = 400, scale = 1.1f),
            )
            GLASS -> WidgetConfig(
                time = WidgetTime(font = ClockFont.OUTFIT, weight = 200, separator = SeparatorStyle.GLYPH, color = WidgetColor.custom(WHITE), separatorColor = WidgetColor.custom(WHITE)),
                details = WidgetDetails(font = ClockFont.OUTFIT, color = WidgetColor.custom(WHITE_MUTED)),
                background = WidgetBackground(color = WidgetColor.custom(WHITE), opacity = 18, cornerRadius = 32),
            )
            SUNSET -> WidgetConfig(
                time = WidgetTime(font = ClockFont.ROUNDED, weight = 700, separator = SeparatorStyle.GLYPH, color = WidgetColor.custom(WHITE), separatorColor = WidgetColor.custom(WHITE)),
                details = WidgetDetails(font = ClockFont.ROUNDED, color = WidgetColor.custom(WHITE_MUTED)),
                background = WidgetBackground(
                    type = WidgetBackgroundType.GRADIENT,
                    color = WidgetColor.custom(0xFFFF7A3D.toInt()),
                    colorB = WidgetColor.custom(0xFF7B2FF7.toInt()),
                    direction = GradientDirection.DIAGONAL_DOWN,
                ),
            )
        }
        return config.copy(
            time = look.time,
            details = look.details,
            background = look.background.copy(photo = config.background.photo),
            alignment = look.alignment,
            padding = look.padding,
        )
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val WHITE_MUTED = 0xCCFFFFFF.toInt()
    }
}
