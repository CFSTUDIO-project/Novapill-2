package app.novapill.android.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingGeometryTest {
    private val sw = 1080
    private val sh = 2400
    private val m = FloatingGeometry.MARGIN_PX

    @Test
    fun initialWindowIsCentredAndInsideTheScreen() {
        val b = FloatingGeometry.initial(sw, sh, 70, 60, minW = 300, minH = 260)
        assertEquals(756, b.w)
        assertEquals(1440, b.h)
        assertEquals((sw - b.w) / 2, b.x)
        assertEquals((sh - b.h) / 2, b.y)
        assertTrue(FloatingGeometry.fitsInside(b, sw, sh))
    }

    @Test
    fun tinyScreensStillGetAWindowThatFits() {
        val b = FloatingGeometry.initial(200, 200, 95, 95, minW = 300, minH = 260)
        assertTrue(FloatingGeometry.fitsInside(b, 200, 200))
        assertTrue(b.w <= 200 - 2 * m)
    }

    @Test
    fun draggingPastAnEdgeStopsAtTheMargin() {
        val start = WindowBox(100, 100, 400, 300)
        val left = FloatingGeometry.move(start, -5_000, 0, sw, sh)
        assertEquals(m, left.x)
        val bottomRight = FloatingGeometry.move(start, 5_000, 5_000, sw, sh)
        assertEquals(sw - m - 400, bottomRight.x)
        assertEquals(sh - m - 300, bottomRight.y)
        assertTrue(FloatingGeometry.fitsInside(bottomRight, sw, sh))
    }

    @Test
    fun resizingNeverShrinksBelowTheMinimum() {
        val b = WindowBox(100, 100, 400, 300)
        val r = FloatingGeometry.resize(b, -10_000, -10_000, minW = 300, minH = 260, screenW = sw, screenH = sh)
        assertEquals(300, r.w)
        assertEquals(260, r.h)
        assertEquals(b.x, r.x)
        assertEquals(b.y, r.y)
    }

    @Test
    fun resizingStopsAtTheScreenEdge() {
        val b = WindowBox(100, 100, 400, 300)
        val r = FloatingGeometry.resize(b, 10_000, 10_000, minW = 300, minH = 260, screenW = sw, screenH = sh)
        assertEquals(sw - m, r.right)
        assertEquals(sh - m, r.bottom)
        assertTrue(FloatingGeometry.fitsInside(r, sw, sh))
    }

    @Test
    fun resizingNearTheEdgeKeepsTheWindowOnScreen() {
        val b = WindowBox(sw - 450, 100, 400, 300)
        val r = FloatingGeometry.resize(b, 200, 0, minW = 300, minH = 260, screenW = sw, screenH = sh)
        assertTrue(FloatingGeometry.fitsInside(r, sw, sh))
    }

    @Test
    fun clampPositionMovesAnOffscreenWindowBackIn() {
        val b = WindowBox(-50, 2500, 400, 300)
        val c = FloatingGeometry.clampPosition(b, sw, sh)
        assertTrue(FloatingGeometry.fitsInside(c, sw, sh))
    }
}
