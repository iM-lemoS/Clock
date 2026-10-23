package io.github.imlemos.clock.alarm.ringing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnoozeDialMathTest {
    private val count = 7 // 5, 10, 15, 20, 30, 45 min, 1 h

    @Test
    fun `letting go near the button chooses nothing`() {
        assertNull(SnoozeDialMath.choiceAt(10f, -10f, count, deadZone = 50f, up = true))
    }

    @Test
    fun `left is the shortest, straight up the middle, right the longest`() {
        assertEquals(0, SnoozeDialMath.choiceAt(-200f, 0f, count, 50f, up = true))
        assertEquals(3, SnoozeDialMath.choiceAt(0f, -200f, count, 50f, up = true))
        assertEquals(6, SnoozeDialMath.choiceAt(200f, 0f, count, 50f, up = true))
    }

    @Test
    fun `dragging below the baseline keeps the nearest end`() {
        assertEquals(0, SnoozeDialMath.choiceAt(-200f, 80f, count, 50f, up = true))
        assertEquals(6, SnoozeDialMath.choiceAt(200f, 80f, count, 50f, up = true))
    }

    @Test
    fun `a dial opening downwards keeps the same order`() {
        assertEquals(3, SnoozeDialMath.choiceAt(0f, 200f, count, 50f, up = false))
        assertEquals(1, SnoozeDialMath.choiceAt(-173f, 100f, count, 50f, up = false))
    }

    @Test
    fun `choices are spread evenly over the half circle, ends lifted`() {
        assertEquals(listOf(165f, 140f, 115f, 90f, 65f, 40f, 15f), SnoozeDialMath.angles(count))
    }
}
