package io.github.imlemos.clock.widget

import org.junit.Test
import kotlin.test.assertEquals

class WidgetLayoutMathTest {

    @Test
    fun `fit size is limited by the tighter side`() {
        // 200 x 50 at size 100: width allows 150, height allows 400.
        assertEquals(150f, WidgetLayoutMath.fitSize(200f, 50f, 300f, 200f), TOLERANCE)
        // Height is the limit now: 50 tall at 100 -> 60 tall at 120.
        assertEquals(120f, WidgetLayoutMath.fitSize(100f, 50f, 1000f, 60f), TOLERANCE)
    }

    @Test
    fun `nothing fits in no space`() {
        assertEquals(0f, WidgetLayoutMath.fitSize(100f, 50f, 0f, 100f))
        assertEquals(0f, WidgetLayoutMath.fitSize(0f, 50f, 100f, 100f))
    }

    @Test
    fun `detail size follows the shorter side within limits`() {
        // min(300, 600 * 0.5) * 0.11 = 33 -> clamped to 30.
        assertEquals(30f, WidgetLayoutMath.detailSize(600f, 300f, 12f, 30f, 1f), TOLERANCE)
        // min(100, 400 * 0.5) * 0.11 = 11 -> raised to 12, then scaled.
        assertEquals(18f, WidgetLayoutMath.detailSize(400f, 100f, 12f, 30f, 1.5f), TOLERANCE)
    }

    @Test
    fun `a line too wide shrinks to fit`() {
        assertEquals(20f, WidgetLayoutMath.lineSize(20f, 100f, 200f))
        assertEquals(10f, WidgetLayoutMath.lineSize(20f, 400f, 200f))
    }

    @Test
    fun `details height counts the gap and every line`() {
        assertEquals(0f, WidgetLayoutMath.detailsHeight(0, 10f))
        assertEquals(10f * (WidgetLayoutMath.DETAIL_GAP + 2 * WidgetLayoutMath.DETAIL_LINE_HEIGHT), WidgetLayoutMath.detailsHeight(2, 10f), TOLERANCE)
    }

    @Test
    fun `alignment places the block inside the bounds`() {
        assertEquals(10f, WidgetLayoutMath.alignedLeft(WidgetAlignment.START, 10f, 110f, 40f))
        assertEquals(40f, WidgetLayoutMath.alignedLeft(WidgetAlignment.CENTER, 10f, 110f, 40f))
        assertEquals(70f, WidgetLayoutMath.alignedLeft(WidgetAlignment.END, 10f, 110f, 40f))
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
