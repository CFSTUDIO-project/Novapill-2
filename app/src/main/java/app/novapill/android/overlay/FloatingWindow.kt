package app.novapill.android.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.novapill.android.core.FloatingGeometry
import app.novapill.android.core.WindowBox
import app.novapill.android.util.Diagnostics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The kinds of floating tool windows NOVA PILL can show. At most one window of each kind is open. */
enum class FloatingKind { CALCULATOR, NOTES, BROWSER, LAUNCHER, GAME_HUD }

/** Static description of one kind of window. Sizes are dp; the percentages give the size of the first opening. */
internal data class FloatingSpec(
    val kind: FloatingKind,
    val title: String,
    /** A focusable window can take keyboard input (notes, browser); the others never steal focus from the app below. */
    val focusable: Boolean,
    val minWidthDp: Int,
    val minHeightDp: Int,
    val widthPct: Int,
    val heightPct: Int,
    /** Height of the strip that drags the window. Negative means the whole window drags, zero means it cannot be dragged. */
    val dragStripDp: Int = 44,
    /** Width at the right end of the drag strip that is left to the content (its buttons). */
    val buttonsZoneDp: Int = 56,
    val resizable: Boolean = true,
    /** Where the window first appears (dp from the top-left corner) instead of the screen centre. */
    val startAtDp: Pair<Int, Int>? = null,
)

/** What a window's content may ask of the window around it. */
interface FloatingHandle {
    /** A context that belongs to this window. Views that must live in it (a WebView) are built from it. */
    val context: Context

    fun close()
}

/**
 * One floating tool window: a WindowManager overlay that hosts Compose. The Compose lifecycle owners are set on
 * the root view that is added to the window (the one Compose looks at), which is what a window without an
 * Activity needs. Dragging and resizing use raw screen coordinates so the window does not shake under the
 * finger. A focusable window is lifted above the keyboard when it opens.
 */
internal class FloatingWindow(
    private val app: Context,
    private val diagnostics: Diagnostics,
    private val spec: FloatingSpec,
    private val startBox: WindowBox?,
    private val onGone: (FloatingKind, WindowBox) -> Unit,
    private val content: @Composable (FloatingHandle) -> Unit,
) : FloatingHandle {
    private val ctx: Context = windowContext()
    private val wm: WindowManager = ctx.getSystemService(WindowManager::class.java)
    private val density = ctx.resources.displayMetrics.density
    private val minW = (spec.minWidthDp * density).roundToInt()
    private val minH = (spec.minHeightDp * density).roundToInt()
    private val owner = OverlayOwner()

    private var box = WindowBox(0, 0, 1, 1)
    private var imeBottom = 0
    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var dragStart = box
    private var resizeStart = box

    override val context: Context get() = ctx

    fun open(): Boolean {
        val (sw, sh) = screenPx()
        var initial = startBox?.let { FloatingGeometry.resize(it, 0, 0, minW, minH, sw, sh) }
            ?: FloatingGeometry.initial(sw, sh, spec.widthPct, spec.heightPct, minW, minH)
        val at = spec.startAtDp
        if (startBox == null && at != null) {
            initial = FloatingGeometry.clampPosition(initial.copy(x = (at.first * density).roundToInt(), y = (at.second * density).roundToInt()), sw, sh)
        }
        box = initial

        val frame = FrameLayout(ctx)
        // The owners go on the view that is added to the window. Compose finds them from the root view, so a
        // ComposeView nested inside a plain layout would crash on attach when only the child had them.
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)

        val cv = ComposeView(ctx)
        cv.setViewTreeLifecycleOwner(owner)
        cv.setViewTreeSavedStateRegistryOwner(owner)
        cv.setContent { content(this) }
        frame.addView(cv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        if (spec.dragStripDp != 0) {
            val stripPx = if (spec.dragStripDp > 0) (spec.dragStripDp * density).roundToInt() else ViewGroup.LayoutParams.MATCH_PARENT
            val zonePx = (spec.buttonsZoneDp * density).roundToInt()
            val drag = HandleView(
                ctx,
                description = "Move the ${spec.title.lowercase()} window",
                onDown = { dragStart = box },
                onDelta = { dx, dy -> moveFrom(dragStart, dx, dy) },
            )
            frame.addView(
                drag,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, stripPx, Gravity.TOP or Gravity.START).apply {
                    rightMargin = zonePx
                },
            )
        }
        if (spec.resizable) {
            val gripPx = (GRIP_DP * density).roundToInt()
            val grip = HandleView(
                ctx,
                description = "Resize the ${spec.title.lowercase()} window",
                onDown = { resizeStart = box },
                onDelta = { dx, dy -> resizeFrom(resizeStart, dx, dy) },
            )
            frame.addView(grip, FrameLayout.LayoutParams(gripPx, gripPx, Gravity.BOTTOM or Gravity.END))
        }
        if (spec.focusable && Build.VERSION.SDK_INT >= 30) {
            frame.setOnApplyWindowInsetsListener { v, insets ->
                val bottom = if (insets.isVisible(WindowInsets.Type.ime())) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
                if (bottom != imeBottom) {
                    imeBottom = bottom
                    v.post { applyBox(shownBox()) }
                }
                insets
            }
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!spec.focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val lp = WindowManager.LayoutParams(box.w, box.h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = box.x
        lp.y = box.y
        lp.title = "NOVA PILL ${spec.title.lowercase()}"
        if (spec.focusable) lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        return try {
            owner.resume()
            wm.addView(frame, lp)
            root = frame
            compose = cv
            params = lp
            true
        } catch (e: RuntimeException) {
            diagnostics.error("floating", e)
            owner.destroy()
            false
        }
    }

    override fun close() {
        val frame = root ?: return
        root = null
        params = null
        try {
            compose?.disposeComposition()
        } catch (e: RuntimeException) {
            diagnostics.error("floating", e)
        }
        compose = null
        try {
            wm.removeViewImmediate(frame)
        } catch (e: IllegalArgumentException) {
            diagnostics.error("floating", e)
        }
        owner.destroy()
        onGone(spec.kind, box)
    }

    /** A rotation or fold changed the screen: pull the window back inside. */
    fun reclamp() {
        val (sw, sh) = screenPx()
        box = FloatingGeometry.resize(box, 0, 0, minW, minH, sw, sh)
        applyBox(shownBox())
    }

    /** The box the user chose, lifted (and shrunk if it must) so the keyboard does not cover it. */
    private fun shownBox(): WindowBox {
        if (imeBottom <= 0) return box
        val (_, sh) = screenPx()
        val room = sh - imeBottom - FloatingGeometry.MARGIN_PX
        val h = min(box.h, max(minH, room - FloatingGeometry.MARGIN_PX))
        val y = if (box.y + h > room) max(FloatingGeometry.MARGIN_PX, room - h) else box.y
        return box.copy(y = y, h = h)
    }

    private fun moveFrom(start: WindowBox, dx: Int, dy: Int) {
        val (sw, sh) = screenPx()
        box = FloatingGeometry.move(start, dx, dy, sw, sh)
        applyBox(shownBox())
    }

    private fun resizeFrom(start: WindowBox, dx: Int, dy: Int) {
        val (sw, sh) = screenPx()
        box = FloatingGeometry.resize(start, dx, dy, minW, minH, sw, sh)
        applyBox(shownBox())
    }

    private fun applyBox(next: WindowBox) {
        val frame = root ?: return
        val lp = params ?: return
        lp.x = next.x
        lp.y = next.y
        lp.width = next.w
        lp.height = next.h
        try {
            wm.updateViewLayout(frame, lp)
        } catch (e: IllegalArgumentException) {
            diagnostics.error("floating", e)
        }
    }

    private fun screenPx(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun windowContext(): Context {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    return app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                }
            } catch (e: RuntimeException) {
                diagnostics.error("floating", e)
            }
        }
        return app
    }

    /** A transparent touch area that reports how far the finger moved on the screen since it went down. */
    private class HandleView(
        context: Context,
        description: String,
        private val onDown: () -> Unit,
        private val onDelta: (Int, Int) -> Unit,
    ) : View(context) {
        private var downX = 0f
        private var downY = 0f

        init {
            contentDescription = description
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    onDown()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    onDelta((event.rawX - downX).roundToInt(), (event.rawY - downY).roundToInt())
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
            }
            return super.onTouchEvent(event)
        }
    }

    private companion object {
        const val GRIP_DP = 36
    }
}
