package app.novapill.android.overlay

import app.novapill.android.core.CardKind
import app.novapill.android.settings.NovaSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandLayoutTest {
    private val s = NovaSettings()
    private val portrait = ScreenInfo(400f, 860f, 2.75f, false, 28f, null)

    @Test
    fun collapsedPillMatchesSettingsAndFitsScreen() {
        val size = IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, portrait)
        assertEquals(176f, size.widthDp, 0.01f)
        assertEquals(38f, size.heightDp, 0.01f)
        assertEquals(19f, size.cornerDp, 0.01f)
        val tiny = ScreenInfo(200f, 400f, 2f, false, 24f, null)
        assertTrue(IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, tiny).widthDp <= 200f * 0.6f + 0.01f)
    }

    @Test
    fun noCutoutPlacesPillCenteredJustBelowTheTopEdge() {
        val size = IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, portrait)
        val p = IslandLayout.placement(size, s, portrait)
        assertEquals(200f, p.centerXDp, 0.01f)
        assertEquals(112f, p.leftDp, 0.01f)
        assertEquals(4f, p.topDp, 0.01f)
        assertNull(p.cutout)
        val moved = IslandLayout.placement(size, s.copy(topOffsetDp = 10, xOffsetDp = 20), portrait)
        assertEquals(14f, moved.topDp, 0.01f)
        assertEquals(220f, moved.centerXDp, 0.01f)
    }

    @Test
    fun punchHoleIsCoveredByCentredCapsuleAndReported() {
        val screen = portrait.copy(topCutout = RectDp(186f, 10f, 214f, 36f))
        val size = IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, screen)
        val p = IslandLayout.placement(size, s, screen)
        assertNotNull(p.cutout)
        val c = p.cutout!!
        assertEquals(0f, c.centerOffsetDp, 0.01f)
        assertEquals(28f, c.widthDp, 0.01f)
        assertEquals(36f, c.bottomDp, 0.01f)
        // capsule is vertically centred on the hole
        assertEquals((10f + 36f) / 2f, p.topDp + size.heightDp / 2f, 0.01f)
    }

    @Test
    fun cutoutAwayFromPillIsIgnored() {
        val screen = portrait.copy(topCutout = RectDp(10f, 0f, 40f, 30f))
        val size = IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, screen)
        assertNull(IslandLayout.placement(size, s, screen).cutout)
    }

    @Test
    fun pillNeverLeavesTheScreen() {
        val size = IslandLayout.size(LayoutRequest(PillKind.COMPACT), s, portrait)
        val p = IslandLayout.placement(size, s.copy(xOffsetDp = 300), portrait)
        assertEquals(308f, p.centerXDp, 0.01f)
        assertTrue(p.leftDp + size.widthDp <= portrait.widthDp - 3.9f)
    }

    @Test
    fun expandedHeightsStayWithinTheSpecRange() {
        fun h(req: LayoutRequest) = IslandLayout.size(req, s, portrait).heightDp
        assertEquals(194f, h(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA, cardCount = 1)), 0.01f)
        assertEquals(252f, h(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA, showVolume = true, cardCount = 2)), 0.01f)
        assertEquals(304f, h(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA, lyricsOpen = true, showVolume = true, cardCount = 2)), 0.01f)
        assertEquals(148f, h(LayoutRequest(PillKind.EXPANDED, CardKind.TIMER)), 0.01f)
        assertTrue(h(LayoutRequest(PillKind.EXPANDED, CardKind.TOOLS, cardCount = 3)) <= 330f)
        assertEquals(IslandLayout.QUICK_PANEL_BODY_DP, h(LayoutRequest(PillKind.EXPANDED, CardKind.TOOLS)), 0.01f)
        assertEquals(344f, IslandLayout.size(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA), s, portrait).widthDp, 0.01f)
    }

    @Test
    fun expandedPanelIsClampedToScreenHeight() {
        val shortScreen = ScreenInfo(800f, 300f, 2f, true, 0f, null)
        val size = IslandLayout.size(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA, lyricsOpen = true), s, shortScreen)
        assertTrue(size.heightDp <= 276f)
    }

    @Test
    fun peekSizesAndWindowMargins() {
        val peek = IslandLayout.size(LayoutRequest(PillKind.PEEK), s, portrait)
        assertEquals(300f, peek.widthDp, 0.01f)
        assertEquals(52f, peek.heightDp, 0.01f)
        assertEquals(68f, IslandLayout.size(LayoutRequest(PillKind.PEEK_CALL), s, portrait).heightDp, 0.01f)
        assertEquals(320f, IslandLayout.windowWidthDp(peek), 0.01f)
        assertEquals(66f, IslandLayout.windowHeightDp(peek), 0.01f)
    }

    @Test
    fun tallPanelsGetTopInsetBelowTheCamera() {
        val screen = portrait.copy(topCutout = RectDp(186f, 10f, 214f, 36f))
        val size = IslandLayout.size(LayoutRequest(PillKind.EXPANDED, CardKind.MEDIA), s, screen)
        val p = IslandLayout.placement(size, s, screen)
        assertEquals(36f - p.topDp + 8f, IslandLayout.contentTopInset(p), 0.01f)
        assertEquals(0f, IslandLayout.contentTopInset(IslandLayout.placement(size, s, portrait)), 0.01f)
    }

    @Test
    fun landscapeCompactUsesNarrowerPill() {
        val land = ScreenInfo(860f, 400f, 2.75f, true, 0f, null)
        val compact = IslandLayout.collapsedWidth(s, land)
        assertEquals(176f * 0.86f, compact, 0.01f)
        assertEquals(176f, IslandLayout.collapsedWidth(s.copy(lsCompact = false), land), 0.01f)
        assertEquals(220f, IslandLayout.collapsedWidth(s.copy(lsIndependent = true, lsWidthDp = 220, lsCompact = false), land), 0.01f)
    }
}
