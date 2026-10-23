package io.github.imlemos.clock.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentPaletteTest {

    @Test
    fun `every preset keeps 3 to 1 contrast on black and on white`() {
        AccentPalette.entries.forEach { accent ->
            val onBlack = contrastRatio(accent.color, Color.Black)
            val onWhite = contrastRatio(accent.color, Color.White)
            assertTrue(onBlack >= 3f, "$accent on black: $onBlack")
            assertTrue(onWhite >= 3f, "$accent on white: $onWhite")
        }
    }

    @Test
    fun `text on the accent reaches 4_5 to 1`() {
        listOf(true, false).forEach { dark ->
            AccentPalette.entries.forEach { accent ->
                val colors = clockColors(isDark = dark, accent = accent.color)
                val ratio = contrastRatio(colors.accent, colors.onAccent)
                assertTrue(ratio >= 4.5f, "$accent (dark=$dark): $ratio")
            }
        }
    }

    @Test
    fun `stored values resolve back to presets`() {
        assertEquals(AccentPalette.Default.color, AccentPalette.colorFor(null))
        assertEquals(AccentPalette.Default, AccentPalette.matching(null))
        assertEquals(AccentPalette.Blue, AccentPalette.matching(AccentPalette.Blue.color.toArgb()))
        assertEquals(null, AccentPalette.matching(0xFF123456.toInt()))
    }

    @Test
    fun `light mode is the inversion of dark mode`() {
        val dark = clockColors(isDark = true)
        val light = clockColors(isDark = false)
        assertEquals(Color.Black, dark.background)
        assertEquals(Color.White, light.background)
        assertEquals(dark.background, light.content)
        assertEquals(dark.content, light.background)
    }

    @Test
    fun `muted text stays readable in both modes`() {
        listOf(true, false).forEach { dark ->
            val colors = clockColors(isDark = dark)
            assertTrue(contrastRatio(colors.contentMuted, colors.background) >= 4.5f)
            assertTrue(contrastRatio(colors.contentMuted, colors.surfaceRaised) >= 4.5f)
        }
    }
}
