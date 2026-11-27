package app.novapill.android.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/** Fullscreen and keyboard state as far as the system reports it to an overlay window (best effort). */
data class SystemUiState(val fullscreen: Boolean = false, val imeVisible: Boolean = false)

/** What the window must look like. Sizes are the pill's; the window adds its own transparent margins. */
data class WindowSpec(
    val visible: Boolean,
    /** Reposition mode: the window covers the screen so the pill can be dragged without moving the window. */
    val fullscreen: Boolean,
    val widthDp: Float,
    val heightDp: Float,
    val leftDp: Float,
    val topDp: Float,
    val watchOutside: Boolean,
    val shrinkDelayMs: Long,
)

/**
 * Owns the TYPE_APPLICATION_OVERLAY window. It never takes focus, keeps the window as small as the pill
 * (so touches elsewhere reach the app underneath), grows the window immediately and shrinks it only after
 * the Compose morph has finished, and re-reads display bounds, insets and cutouts whenever asked.
 */
class IslandWindowController(private val service: Context) {
    private class Applied(val w: Int, val h: Int, val x: Int, val y: Int, val flags: Int)

    private val handler = Handler(Looper.getMainLooper())
    private val owner = OverlayOwner()
    private val ctx: Context = createWindowContext()
    private val wm: WindowManager = ctx.getSystemService(WindowManager::class.java)

    private var view: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null
    private var applied: Applied? = null
    private var desired: WindowSpec? = null
    private var shrink: Runnable? = null
    private var onOutside: (() -> Unit)? = null

    private val _screen = MutableStateFlow(ScreenInfo.DEFAULT)
    val screen: StateFlow<ScreenInfo> = _screen.asStateFlow()

    private val _systemUi = MutableStateFlow(SystemUiState())
    val systemUi: StateFlow<SystemUiState> = _systemUi.asStateFlow()

    var lastError: String? = null
        private set

    val isAttached: Boolean get() = view != null

    private fun createWindowContext(): Context {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val display = service.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    return service.createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                }
            } catch (_: Exception) {
                // fall back to the service context, which can also host an application overlay
            }
        }
        return service
    }

    /** Adds the overlay window. Returns false (and keeps [lastError]) when Android refuses. */
    fun attach(outsideTouch: () -> Unit, content: @Composable () -> Unit): Boolean {
        if (view != null) return true
        onOutside = outsideTouch
        return try {
            val cv = ComposeView(ctx)
            cv.setViewTreeLifecycleOwner(owner)
            cv.setViewTreeSavedStateRegistryOwner(owner)
            cv.setContent(content)
            cv.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                    onOutside?.invoke()
                    true
                } else {
                    false
                }
            }
            cv.setOnApplyWindowInsetsListener { v, insets ->
                readSystemUi(v, insets)
                insets
            }
            owner.resume()
            val lp = baseParams()
            wm.addView(cv, lp)
            view = cv
            params = lp
            applied = Applied(lp.width, lp.height, lp.x, lp.y, lp.flags)
            refreshScreen()
            lastError = null
            true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}${e.message?.let { ": " + it.take(80) } ?: ""}"
            false
        }
    }

    fun detach() {
        shrink?.let { handler.removeCallbacks(it) }
        shrink = null
        val v = view
        view = null
        if (v != null) {
            try {
                v.disposeComposition()
            } catch (_: Exception) {
                // already disposed
            }
            try {
                wm.removeViewImmediate(v)
            } catch (_: Exception) {
                // already removed by the system
            }
        }
        owner.destroy()
        params = null
        applied = null
        desired = null
        onOutside = null
    }

    fun haptic() {
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // ---- window geometry ----

    private fun baseParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 0
        lp.y = 0
        lp.title = "NOVA PILL"
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            lp.setFitInsetsTypes(0)
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    fun apply(spec: WindowSpec) {
        desired = spec
        if (view == null) return
        val target = targetOf(spec)
        val cur = applied ?: return
        val grows = target.w >= cur.w && target.h >= cur.h
        if (grows) {
            cancelShrink()
            commit(target)
            return
        }
        // Shrinking: keep the larger window (centred on the new position) until the morph is done.
        val holdW = max(target.w, cur.w)
        val holdH = max(target.h, cur.h)
        val holdX = target.x + target.w / 2 - holdW / 2
        commit(Applied(holdW, holdH, holdX, target.y, target.flags))
        cancelShrink()
        val task = Runnable {
            shrink = null
            desired?.let { commit(targetOf(it)) }
        }
        shrink = task
        handler.postDelayed(task, spec.shrinkDelayMs)
    }

    private fun cancelShrink() {
        shrink?.let { handler.removeCallbacks(it) }
        shrink = null
    }

    private fun targetOf(spec: WindowSpec): Applied {
        val d = ctx.resources.displayMetrics.density
        var flags = BASE_FLAGS
        if (!spec.visible) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (spec.visible && spec.watchOutside) flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val cur = applied
        if (!spec.visible) {
            return Applied(1, 1, cur?.x ?: 0, cur?.y ?: 0, flags)
        }
        if (spec.fullscreen) {
            val s = _screen.value
            return Applied((s.widthDp * d).roundToInt(), (s.heightDp * d).roundToInt(), 0, 0, flags)
        }
        val w = ((spec.widthDp + 2f * IslandLayout.WINDOW_PAD_H_DP) * d).roundToInt()
        val h = ((spec.heightDp + IslandLayout.WINDOW_PAD_BOTTOM_DP) * d).roundToInt()
        val x = ((spec.leftDp - IslandLayout.WINDOW_PAD_H_DP) * d).roundToInt()
        val y = (spec.topDp * d).roundToInt()
        return Applied(w, h, x, y, flags)
    }

    private fun commit(t: Applied) {
        val v = view ?: return
        val lp = params ?: return
        val cur = applied
        if (cur != null && cur.w == t.w && cur.h == t.h && cur.x == t.x && cur.y == t.y && cur.flags == t.flags) return
        lp.width = t.w
        lp.height = t.h
        lp.x = t.x
        lp.y = t.y
        lp.flags = t.flags
        try {
            wm.updateViewLayout(v, lp)
            applied = t
        } catch (e: IllegalArgumentException) {
            lastError = "updateViewLayout: ${e.message?.take(80)}"
        }
    }

    // ---- screen metrics, cutout, system bars ----

    /** Re-reads display bounds, status bar height and the top cutout for the current rotation. */
    fun refreshScreen() {
        try {
            _screen.value = readScreen()
        } catch (e: Exception) {
            lastError = "screen: ${e.javaClass.simpleName}"
        }
    }

    private fun readScreen(): ScreenInfo {
        val density = ctx.resources.displayMetrics.density
        val bounds: Rect
        val statusPx: Int
        var cutout: Rect? = null

        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.maximumWindowMetrics
            bounds = metrics.bounds
            val insets = metrics.windowInsets
            statusPx = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            cutout = insets.displayCutout?.boundingRects?.firstOrNull { it.top <= 0 && it.width() < bounds.width() * 0.7f }
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            bounds = Rect(0, 0, dm.widthPixels, dm.heightPixels)
            val id = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
            statusPx = if (id > 0) ctx.resources.getDimensionPixelSize(id) else (24 * density).roundToInt()
            if (Build.VERSION.SDK_INT >= 28) {
                cutout = view?.rootWindowInsets?.displayCutout?.boundingRects
                    ?.firstOrNull { it.top <= 0 && it.width() < bounds.width() * 0.7f }
            }
        }

        val cutoutDp = cutout?.let {
            RectDp(it.left / density, it.top / density, it.right / density, it.bottom / density)
        }
        return ScreenInfo(
            widthDp = bounds.width() / density,
            heightDp = bounds.height() / density,
            density = density,
            landscape = bounds.width() > bounds.height(),
            statusBarDp = statusPx / density,
            topCutout = cutoutDp,
        )
    }

    private fun readSystemUi(v: View, insets: WindowInsets) {
        val fullscreen: Boolean
        val ime: Boolean
        if (Build.VERSION.SDK_INT >= 30) {
            fullscreen = !insets.isVisible(WindowInsets.Type.statusBars())
            ime = insets.isVisible(WindowInsets.Type.ime())
        } else {
            @Suppress("DEPRECATION")
            fullscreen = (v.windowSystemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
            ime = false
        }
        _systemUi.value = SystemUiState(fullscreen, ime)
        if (Build.VERSION.SDK_INT in 28..29) refreshScreen()
    }

    private companion object {
        const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    }
}
