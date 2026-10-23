package io.github.imlemos.clock.core.designsystem.glass

import org.junit.Test
import kotlin.test.assertEquals

class GlassLevelTest {

    @Test
    fun `android 13 and later get full glass`() {
        assertEquals(GlassLevel.Full, resolveGlassLevel(false, false, sdkInt = 33))
        assertEquals(GlassLevel.Full, resolveGlassLevel(false, false, sdkInt = 36))
    }

    @Test
    fun `android 12 gets blur without refraction`() {
        assertEquals(GlassLevel.BlurOnly, resolveGlassLevel(false, false, sdkInt = 31))
        assertEquals(GlassLevel.BlurOnly, resolveGlassLevel(false, false, sdkInt = 32))
    }

    @Test
    fun `reduce transparency or a system request forces solid surfaces`() {
        assertEquals(GlassLevel.Solid, resolveGlassLevel(reduceTransparency = true, systemReducesEffects = false, sdkInt = 36))
        assertEquals(GlassLevel.Solid, resolveGlassLevel(reduceTransparency = false, systemReducesEffects = true, sdkInt = 36))
        assertEquals(GlassLevel.Solid, resolveGlassLevel(reduceTransparency = true, systemReducesEffects = false, sdkInt = 31))
    }
}
