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

    @Test
    fun `overlapping elements are pushed apart, top to bottom`() {
        val clock = Box2(100f, 100f, 400f, 200f)
        val date = Box2(150f, 250f, 300f, 50f)
        val label = Box2(100f, 900f, 400f, 80f)
        val (c, d, l) = LayoutGeometry.separate(listOf(clock, date, label), phone, gap = 10f)
        assertEquals(clock, c)
        assertEquals(310f, d.y)
        assertEquals(label, l)
    }

    @Test
    fun `side by side elements are left alone`() {
        val left = Box2(0f, 100f, 300f, 200f)
        val right = Box2(500f, 150f, 300f, 200f)
        assertEquals(listOf(left, right), LayoutGeometry.separate(listOf(left, right), phone, gap = 10f))
    }

    @Test
    fun `a chain of pushes stops at the bottom edge`() {
        val short = Size2(1080f, 500f)
        val boxes = listOf(Box2(0f, 0f, 500f, 200f), Box2(0f, 100f, 500f, 200f), Box2(0f, 200f, 500f, 200f))
        val result = LayoutGeometry.separate(boxes, short, gap = 0f)
        assertEquals(200f, result[1].y)
        assertEquals(300f, result[2].y)
    }

    @Test
    fun `a photo with the screen's proportions only moves once zoomed`() {
        val aspect = phone.width / phone.height
        assertEquals(Frame(1f, 0f, 0f), LayoutGeometry.clampFrame(Frame(0.5f, 0.3f, -0.3f), phone, aspect))
        assertEquals(Frame(2f, 0.5f, -0.5f), LayoutGeometry.clampFrame(Frame(2f, 0.9f, -0.9f), phone, aspect))
        assertEquals(LayoutGeometry.MAX_BACKGROUND_ZOOM, LayoutGeometry.clampFrame(Frame(10f), phone, aspect).zoom)
    }

    @Test
    fun `a wide photo on a tall screen moves sideways without zoom, not up and down`() {
        // 4:3 photo on 1080x2200: covering the height makes it 2933 px wide, 1853 px overflow.
        val frame = LayoutGeometry.clampFrame(Frame(1f, 5f, 5f), phone, 4f / 3f)
        assertEquals((2200f * 4f / 3f - 1080f) / 2f / 1080f, frame.offsetX, 0.0001f)
        assertEquals(0f, frame.offsetY)
    }

    @Test
    fun `dragging and pinching move and zoom the photo`() {
        val aspect = phone.width / phone.height
        val zoomed = LayoutGeometry.transformFrame(Frame(), dx = 0f, dy = 0f, zoom = 2f, area = phone, mediaAspect = aspect)
        assertEquals(2f, zoomed.zoom)
        val moved = LayoutGeometry.transformFrame(zoomed, dx = 108f, dy = -220f, zoom = 1f, area = phone, mediaAspect = aspect)
        assertEquals(0.1f, moved.offsetX, 0.0001f)
        assertEquals(-0.1f, moved.offsetY, 0.0001f)
    }

    @Test
    fun `layouts saved with the first clock styles still read`() {
        val mono = LayoutJson.decode("""{"clock":{"style":"MONO"}}""").clock
        assertEquals(ClockStyle.DIGITAL, mono.style)
        assertEquals(ClockFont.MONO, mono.font)
        assertEquals(ClockFont.DOTO, LayoutJson.decode("""{"clock":{"style":"DOTS"}}""").clock.font)
    }

    @Test
    fun `a tall element at the top stays above shorter ones it overlaps`() {
        val clock = Box2(100f, 0f, 400f, 900f)
        val date = Box2(100f, 300f, 400f, 50f)
        val (c, d) = LayoutGeometry.separate(listOf(clock, date), phone, gap = 10f)
        assertEquals(clock, c)
        assertEquals(910f, d.y)
    }
}
