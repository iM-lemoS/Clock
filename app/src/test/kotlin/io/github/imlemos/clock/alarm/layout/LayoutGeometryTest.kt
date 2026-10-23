package io.github.imlemos.clock.alarm.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutGeometryTest {
    private val phone = Size2(1080f, 2200f)

    @Test
    fun `an element is centred on its placement`() {
        val topLeft = LayoutGeometry.topLeft(Placement(0.5f, 0.5f), Size2(200f, 100f), phone)
        assertEquals(Point2(440f, 1050f), topLeft)
    }

    @Test
    fun `an element near an edge is moved back inside`() {
        assertEquals(Point2(0f, 0f), LayoutGeometry.topLeft(Placement(0f, 0f), Size2(200f, 100f), phone))
        assertEquals(Point2(880f, 2100f), LayoutGeometry.topLeft(Placement(1f, 1f), Size2(200f, 100f), phone))
    }

    @Test
    fun `the same layout stays inside a small, wide screen`() {
        val landscape = Size2(1600f, 700f)
        val size = Size2(400f, 300f)
        val topLeft = LayoutGeometry.topLeft(Placement(0.5f, 0.95f), size, landscape)
        assertEquals(size.height, landscape.height - topLeft.y)
    }

    @Test
    fun `scale is limited to the allowed range`() {
        val base = Size2(100f, 100f)
        assertEquals(LayoutGeometry.MAX_SCALE, LayoutGeometry.effectiveScale(9f, base, phone))
        assertEquals(LayoutGeometry.MIN_SCALE, LayoutGeometry.effectiveScale(0.1f, base, phone))
    }

    @Test
    fun `an element too large for the screen is shrunk to fit`() {
        assertEquals(0.5f, LayoutGeometry.effectiveScale(2f, Size2(2160f, 100f), phone), 0.0001f)
    }

    @Test
    fun `controls keep their minimum size`() {
        val scale = LayoutGeometry.effectiveScale(
            0.5f, Size2(500f, 300f), phone, LayoutGeometry.CONTROLS_MIN_SCALE, LayoutGeometry.CONTROLS_MAX_SCALE,
        )
        assertEquals(1f, scale)
    }

    @Test
    fun `dragging stops where the element would leave the area`() {
        val moved = LayoutGeometry.moved(Placement(0.5f, 0.5f), dx = 5000f, dy = -5000f, Size2(216f, 220f), phone)
        assertEquals(0.9f, moved.x, 0.0001f)
        assertEquals(0.05f, moved.y, 0.0001f)
    }

    @Test
    fun `layout JSON round-trips and unreadable JSON falls back to the default`() {
        val spec = PresetLayouts.specs.getValue(PresetLayouts.ANALOG).copy(background = Background.Video("/v.mp4", 0.5f))
        assertEquals(spec, LayoutJson.decode(LayoutJson.encode(spec)))
        assertEquals(LayoutSpec(), LayoutJson.decode("not json"))
    }
}
