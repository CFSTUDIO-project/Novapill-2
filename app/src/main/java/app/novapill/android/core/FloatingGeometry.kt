package app.novapill.android.core

import kotlin.math.max
import kotlin.math.min

/** A window rectangle in screen pixels. */
data class WindowBox(val x: Int, val y: Int, val w: Int, val h: Int) {
    val right: Int get() = x + w
    val bottom: Int get() = y + h
}

/**
 * Window maths for the floating tools. A window always stays fully on screen, keeps a margin from the edges
 * and never shrinks below its minimum. No Android types, so every rule here is unit tested.
 */
object FloatingGeometry {
    const val MARGIN_PX = 8

    /** Centred on screen at [widthPct] / [heightPct] of it, clamped to the minimum and to the margins. */
    fun initial(screenW: Int, screenH: Int, widthPct: Int, heightPct: Int, minW: Int, minH: Int): WindowBox {
        val availW = max(1, screenW - 2 * MARGIN_PX)
        val availH = max(1, screenH - 2 * MARGIN_PX)
        val w = (screenW * widthPct / 100).coerceIn(min(minW, availW), availW)
        val h = (screenH * heightPct / 100).coerceIn(min(minH, availH), availH)
        return WindowBox((screenW - w) / 2, (screenH - h) / 2, w, h)
    }

    fun move(b: WindowBox, dx: Int, dy: Int, screenW: Int, screenH: Int): WindowBox =
        clampPosition(b.copy(x = b.x + dx, y = b.y + dy), screenW, screenH)

    /** Resizes from the bottom-right corner. The window cannot grow past the screen edge or shrink below the minimum. */
    fun resize(b: WindowBox, dw: Int, dh: Int, minW: Int, minH: Int, screenW: Int, screenH: Int): WindowBox {
        val maxW = max(1, screenW - MARGIN_PX - b.x)
        val maxH = max(1, screenH - MARGIN_PX - b.y)
        val w = (b.w + dw).coerceIn(min(minW, maxW), maxW)
        val h = (b.h + dh).coerceIn(min(minH, maxH), maxH)
        return clampPosition(b.copy(w = w, h = h), screenW, screenH)
    }

    fun clampPosition(b: WindowBox, screenW: Int, screenH: Int): WindowBox {
        val x = b.x.coerceIn(MARGIN_PX, max(MARGIN_PX, screenW - MARGIN_PX - b.w))
        val y = b.y.coerceIn(MARGIN_PX, max(MARGIN_PX, screenH - MARGIN_PX - b.h))
        return b.copy(x = x, y = y)
    }

    fun fitsInside(b: WindowBox, screenW: Int, screenH: Int): Boolean =
        b.x >= 0 && b.y >= 0 && b.right <= screenW && b.bottom <= screenH
}
