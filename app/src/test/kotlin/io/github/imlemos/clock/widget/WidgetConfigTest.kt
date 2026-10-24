package io.github.imlemos.clock.widget

import io.github.imlemos.clock.alarm.layout.ClockFont
import io.github.imlemos.clock.alarm.layout.DateParts
import io.github.imlemos.clock.alarm.layout.GradientDirection
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `config round-trips through JSON`() {
        val config = WidgetConfig(
            time = WidgetTime(font = ClockFont.SERIF, weight = 600, arrangement = TimeArrangement.STACKED, color = WidgetColor.custom(0x80FF0000.toInt())),
            details = WidgetDetails(font = ClockFont.ROUNDED, scale = 1.3f),
            background = WidgetBackground(type = WidgetBackgroundType.GRADIENT, direction = GradientDirection.DIAGONAL_UP, opacity = 40, cornerRadius = 12),
            alignment = WidgetAlignment.END,
            date = DateParts(year = true),
            showSkip = false,
        )
        assertEquals(config, json.decodeFromString<WidgetConfig>(json.encodeToString(WidgetConfig.serializer(), config)))
    }

    @Test
    fun `older or newer saved configs still load`() {
        // Unknown keys are ignored and missing ones take their defaults.
        val loaded = json.decodeFromString<WidgetConfig>("""{"padding":8,"futureOption":true}""")
        assertEquals(WidgetConfig(padding = 8), loaded)
    }

    @Test
    fun `theme colours need a light and a dark image`() {
        assertTrue(WidgetConfig().themed)
        val fixed = WidgetConfig(
            time = WidgetTime(color = WidgetColor.Accent, separatorColor = WidgetColor.custom(-1)),
            details = WidgetDetails(color = WidgetColor.custom(-1)),
            background = WidgetBackground(type = WidgetBackgroundType.NONE),
        )
        assertFalse(fixed.themed)
        // A solid background in the theme's surface colour makes it themed again.
        assertTrue(fixed.copy(background = WidgetBackground(type = WidgetBackgroundType.SOLID)).themed)
    }

    @Test
    fun `presets change the look but keep what the widget shows`() {
        val config = WidgetConfig(showDate = false, showLabel = false, date = DateParts(weekday = false), background = WidgetBackground(photo = "a.jpg"))
        WidgetPreset.entries.forEach { preset ->
            val styled = preset.applyTo(config)
            assertEquals(config.showDate, styled.showDate)
            assertEquals(config.showLabel, styled.showLabel)
            assertEquals(config.date, styled.date)
            assertEquals("a.jpg", styled.background.photo)
        }
    }

    @Test
    fun `preset weights are within their fonts' ranges`() {
        WidgetPreset.entries.map { it.applyTo(WidgetConfig()) }.forEach { config ->
            config.time.weight?.let { assertEquals(it, WidgetRenderer.effectiveWeight(config.time.font, it)) }
            config.details.weight?.let { assertEquals(it, WidgetRenderer.effectiveWeight(config.details.font, it)) }
        }
    }

    @Test
    fun `time splits for 24-hour clocks`() {
        val time = splitTime(LocalTime.of(7, 5), is24Hour = true, Locale.UK)
        assertEquals(SplitTime("07", "05", null), time)
    }

    @Test
    fun `time splits for 12-hour clocks`() {
        val morning = splitTime(LocalTime.of(0, 30), is24Hour = false, Locale.US)
        assertEquals("12", morning.hours)
        assertEquals("30", morning.minutes)
        assertEquals("AM", morning.dayPeriod)
        val afternoon = splitTime(LocalTime.of(13, 0), is24Hour = false, Locale.US)
        assertEquals("1", afternoon.hours)
        assertEquals("PM", afternoon.dayPeriod)
        assertNull(splitTime(LocalTime.NOON, is24Hour = true, Locale.US).dayPeriod)
    }
}
