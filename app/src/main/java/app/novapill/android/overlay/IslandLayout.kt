package app.novapill.android.overlay

import app.novapill.android.core.CardKind
import app.novapill.android.settings.IdleInfo
import app.novapill.android.settings.NovaSettings
import kotlin.math.max
import kotlin.math.min

/** Everything here is in dp and in the coordinate space of the current display rotation. */
data class RectDp(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val centerX: Float get() = (left + right) / 2f
}

data class ScreenInfo(
    val widthDp: Float,
    val heightDp: Float,
    val density: Float,
    val landscape: Boolean,
    val statusBarDp: Float,
    /** Cutout touching the top edge of the current orientation, if any. */
    val topCutout: RectDp?,
) {
    companion object {
        val DEFAULT = ScreenInfo(400f, 860f, 2.75f, false, 28f, null)
    }
}

enum class PillKind { IDLE, COMPACT, PEEK, PEEK_CALL, EXPANDED }

data class PillSize(val widthDp: Float, val heightDp: Float, val cornerDp: Float)

data class CutoutInfo(
    /** Cutout centre minus pill centre (dp). Constant while the pill morphs. */
    val centerOffsetDp: Float,
    val widthDp: Float,
    /** Cutout bottom in screen coordinates (dp). */
    val bottomDp: Float,
)

data class Placement(
    /** Pill left/top in screen coordinates for the requested size. */
    val leftDp: Float,
    val topDp: Float,
    val centerXDp: Float,
    val cutout: CutoutInfo?,
)

data class LayoutRequest(
    val kind: PillKind,
    val card: CardKind? = null,
    val lyricsOpen: Boolean = false,
    val showVolume: Boolean = false,
    val showProgress: Boolean = true,
    val cardCount: Int = 1,
    /** How much the idle pill shows: clock only, clock and battery, or clock, weather and battery. */
    val idleInfo: IdleInfo = IdleInfo.CLOCK,
    /** A peek with a message body is taller so the sender and the text both fit. */
    val peekTall: Boolean = false,
    /** Width of a camera hole that sits under the idle pill; the pill widens so its content stays beside the hole. */
    val cutoutWidthDp: Float = 0f,
)

object IslandLayout {
    /** Transparent window margin that keeps the soft shadow from being clipped. */
    const val WINDOW_PAD_H_DP = 10f
    const val WINDOW_PAD_BOTTOM_DP = 14f
    private const val MIN_COMPACT_DP = 96f
    private const val EDGE_MARGIN_DP = 4f
    private const val CUTOUT_CONTENT_MARGIN_DP = 8f
    private const val TALL_PEEK_DP = 68f
    private const val IDLE_SIDE_DP = 52f
    private const val IDLE_SIDE_FULL_DP = 84f
    private const val IDLE_SIDE_PAD_DP = 10f
    /** Extra clearance between the camera hole and the idle pill's content. */
    const val CUTOUT_GAP_DP = 14f
    /** Fixed height of the quick panel so switching pages never resizes the window. */
    const val QUICK_PANEL_BODY_DP = 300f

    fun collapsedWidth(s: NovaSettings, screen: ScreenInfo): Float {
        val base = s.effectiveWidthDp(screen.landscape).toFloat()
        val adapted = if (screen.landscape && s.lsAutoAdapt && s.lsCompact) base * 0.86f else base
        return adapted.coerceIn(MIN_COMPACT_DP, max(MIN_COMPACT_DP, screen.widthDp * 0.6f))
    }

    fun collapsedHeight(s: NovaSettings): Float = s.heightDp.toFloat()

    fun expandedWidth(s: NovaSettings, screen: ScreenInfo): Float {
        val cap = if (screen.landscape && s.lsAutoAdapt && s.lsCompact) 320f else 344f
        val frac = if (screen.landscape) 0.6f else 0.92f
        return min(cap, screen.widthDp * frac).coerceAtLeast(MIN_COMPACT_DP)
    }

    fun size(req: LayoutRequest, s: NovaSettings, screen: ScreenInfo): PillSize {
        val cw = collapsedWidth(s, screen)
        val ch = collapsedHeight(s)
        val pct = s.cornerPercent / 100f
        return when (req.kind) {
            PillKind.IDLE -> {
                var w = when (req.idleInfo) {
                    IdleInfo.CLOCK -> max(84f, cw * 0.52f)
                    IdleInfo.CLOCK_BATTERY -> max(120f, cw * 0.72f)
                    IdleInfo.FULL -> max(180f, cw + 60f)
                }
                if (req.cutoutWidthDp > 0f) {
                    // Clock on one side of the camera hole, the rest on the other; nothing is drawn over the hole.
                    val side = if (req.idleInfo == IdleInfo.FULL) IDLE_SIDE_FULL_DP else IDLE_SIDE_DP
                    w = max(w, req.cutoutWidthDp + CUTOUT_GAP_DP + 2f * (side + IDLE_SIDE_PAD_DP))
                }
                PillSize(w.coerceAtMost(screen.widthDp * 0.9f), ch, corner(ch, 999f, pct))
            }
            PillKind.COMPACT -> PillSize(cw, ch, corner(ch, 999f, pct))
            PillKind.PEEK -> {
                val h = if (req.peekTall) max(TALL_PEEK_DP, ch + 26f) else max(52f, ch + 14f)
                val w = min(max(cw + 124f, 300f), min(340f, screen.widthDp * 0.9f))
                PillSize(w, h, corner(h, 28f, pct))
            }
            PillKind.PEEK_CALL -> {
                val h = 68f
                val w = min(max(cw + 124f, 316f), min(344f, screen.widthDp * 0.92f))
                PillSize(w, h, corner(h, 30f, pct))
            }
            PillKind.EXPANDED -> {
                val w = expandedWidth(s, screen)
                var h = expandedHeight(req, s, screen)
                val room = screen.heightDp - 24f
                h = min(h, room)
                PillSize(w, h, corner(h, 34f, pct))
            }
        }
    }

    private fun corner(height: Float, maxRadius: Float, pct: Float): Float = min(height / 2f, maxRadius) * pct

    private fun expandedHeight(req: LayoutRequest, s: NovaSettings, screen: ScreenInfo): Float {
        val dots = if (req.cardCount > 1) 14f else 0f
        val compactLs = screen.landscape && s.lsAutoAdapt && s.lsCompact
        val body = when (req.card) {
            CardKind.MEDIA -> {
                val art = if (compactLs) min(s.artworkSizeDp.toFloat(), 44f) else s.artworkSizeDp.toFloat()
                var h = 16f + art + 10f + 56f + 16f
                if (req.showProgress) h += 40f
                if (req.lyricsOpen) h += 96f else if (req.showVolume) h += 44f
                h
            }
            CardKind.TIMER, CardKind.STOPWATCH -> 148f
            CardKind.RECORD -> 164f
            // Quick panel: header 48, page tabs 24, page 168 (scrolls inside), status strip 18, paddings 12 + 6 + 6 + 6 + 12.
            CardKind.TOOLS, null -> QUICK_PANEL_BODY_DP
        }
        return body + dots
    }

    /**
     * Anchor of the pill. With a top camera cutout the collapsed pill is vertically centred on it, so the
     * black capsule blends into the hole; without one it sits just below the status-bar edge.
     */
    fun placement(size: PillSize, s: NovaSettings, screen: ScreenInfo): Placement {
        val ch = collapsedHeight(s)
        val offsetX = s.effectiveXOffsetDp(screen.landscape).toFloat()
        val cutout = screen.topCutout
        val baseTop = if (cutout != null) {
            ((cutout.top + cutout.bottom) / 2f - ch / 2f).coerceAtLeast(2f)
        } else {
            max(4f, (screen.statusBarDp - ch) / 2f + 4f)
        }
        val top = (baseTop + s.effectiveTopOffsetDp(screen.landscape)).coerceAtLeast(0f)

        val wanted = screen.widthDp / 2f + offsetX
        val half = size.widthDp / 2f
        val minCenter = EDGE_MARGIN_DP + half
        val maxCenter = screen.widthDp - EDGE_MARGIN_DP - half
        val center = if (minCenter <= maxCenter) wanted.coerceIn(minCenter, maxCenter) else screen.widthDp / 2f

        val left = center - half
        val info = cutout?.let {
            val overlapsX = it.right > left && it.left < left + size.widthDp
            val overlapsY = it.top < top + size.heightDp
            if (overlapsX && overlapsY) CutoutInfo(it.centerX - center, it.width, it.bottom) else null
        }
        return Placement(left, top, center, info)
    }

    /** Extra top padding a tall panel needs so its content starts below the camera hole. */
    fun contentTopInset(placement: Placement): Float {
        val c = placement.cutout ?: return 0f
        return max(0f, c.bottomDp - placement.topDp + CUTOUT_CONTENT_MARGIN_DP)
    }

    fun windowWidthDp(size: PillSize): Float = size.widthDp + 2f * WINDOW_PAD_H_DP

    fun windowHeightDp(size: PillSize): Float = size.heightDp + WINDOW_PAD_BOTTOM_DP
}
