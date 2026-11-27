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
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.novapill.android.core.WindowBox
import app.novapill.android.core.CalcPad
import app.novapill.android.core.FloatingGeometry
import app.novapill.android.overlay.ui.FloatingCalculator
import app.novapill.android.settings.NovaSettings
import app.novapill.android.util.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Floating tool windows that live inside NOVA PILL's own overlay permission (no system freeform needed).
 * Today there is one tool, the calculator, and at most one instance of it. The window is always kept fully
 * on screen: dragging and resizing go through [FloatingGeometry]. Positions use raw screen coordinates, so
 * the window does not shake while it follows the finger.
 */
class FloatingManager(private val app: Context, private val diagnostics: Diagnostics) {
    private var calculator: CalculatorWindow? = null

    private val _calculatorOpen = MutableStateFlow(false)
    val calculatorOpen: StateFlow<Boolean> = _calculatorOpen.asStateFlow()

    /** Opens the calculator, or closes it when it is already open. Returns false when Android refuses the window. */
    fun toggleCalculator(settings: NovaSettings): Boolean {
        val existing = calculator
        if (existing != null) {
            existing.close()
            return true
        }
        val window = CalculatorWindow(settings)
        if (!window.open()) return false
        calculator = window
        _calculatorOpen.value = true
        return true
    }

    fun closeAll() {
        calculator?.close()
    }

    /** A rotation or fold changed the screen: pull every window back inside. */
    fun onConfigurationChanged() {
        calculator?.reclamp()
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

    private inner class CalculatorWindow(settings: NovaSettings) {
        private val ctx = windowContext()
        private val wm: WindowManager = ctx.getSystemService(WindowManager::class.java)
        private val density = ctx.resources.displayMetrics.density
        private val minW = (MIN_WIDTH_DP * density).roundToInt()
        private val minH = (MIN_HEIGHT_DP * density).roundToInt()
        private val owner = OverlayOwner()
        private val widthPct = settings.floatingWidthPct
        private val heightPct = settings.floatingHeightPct

        private var pad by mutableStateOf(CalcPad())
        private var box = WindowBox(0, 0, 1, 1)
        private var root: FrameLayout? = null
        private var params: WindowManager.LayoutParams? = null

        // gesture start snapshots
        private var dragStart = box
        private var resizeStart = box

        fun open(): Boolean {
            val (sw, sh) = screenPx()
            box = FloatingGeometry.initial(sw, sh, widthPct, heightPct, minW, minH)
            val compose = ComposeView(ctx).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    FloatingCalculator(
                        pad = pad,
                        onKey = { key -> pad = CalcPad.press(pad, key) },
                        onClose = { close() },
                    )
                }
            }
            val frame = FrameLayout(ctx)
            frame.addView(compose, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            val titleBarPx = (TITLE_BAR_DP * density).roundToInt()
            val closeZonePx = (CLOSE_ZONE_DP * density).roundToInt()
            val gripPx = (GRIP_DP * density).roundToInt()
            val dragHandle = HandleView(
                ctx,
                description = "Move the calculator window",
                onDown = { dragStart = box },
                onDelta = { dx, dy -> moveFrom(dragStart, dx, dy) },
            )
            frame.addView(
                dragHandle,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, titleBarPx, Gravity.TOP or Gravity.START).apply {
                    rightMargin = closeZonePx
                },
            )
            val resizeHandle = HandleView(
                ctx,
                description = "Resize the calculator window",
                onDown = { resizeStart = box },
                onDelta = { dx, dy -> resizeFrom(resizeStart, dx, dy) },
            )
            frame.addView(resizeHandle, FrameLayout.LayoutParams(gripPx, gripPx, Gravity.BOTTOM or Gravity.END))

            val lp = WindowManager.LayoutParams(
                box.w,
                box.h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            )
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = box.x
            lp.y = box.y
            lp.title = "NOVA PILL calculator"
            return try {
                wm.addView(frame, lp)
                owner.resume()
                root = frame
                params = lp
                true
            } catch (e: WindowManager.BadTokenException) {
                diagnostics.error("floating", e)
                owner.destroy()
                false
            } catch (e: SecurityException) {
                diagnostics.error("floating", e)
                owner.destroy()
                false
            } catch (e: RuntimeException) {
                diagnostics.error("floating", e)
                owner.destroy()
                false
            }
        }

        fun close() {
            val frame = root ?: return
            root = null
            params = null
            try {
                wm.removeViewImmediate(frame)
            } catch (e: IllegalArgumentException) {
                diagnostics.error("floating", e)
            }
            owner.destroy()
            calculator = null
            _calculatorOpen.value = false
        }

        fun reclamp() {
            val (sw, sh) = screenPx()
            applyBox(FloatingGeometry.resize(box, 0, 0, minW, minH, sw, sh))
        }

        private fun moveFrom(start: WindowBox, dx: Int, dy: Int) {
            val (sw, sh) = screenPx()
            applyBox(FloatingGeometry.move(start, dx, dy, sw, sh))
        }

        private fun resizeFrom(start: WindowBox, dx: Int, dy: Int) {
            val (sw, sh) = screenPx()
            applyBox(FloatingGeometry.resize(start, dx, dy, minW, minH, sw, sh))
        }

        private fun applyBox(next: WindowBox) {
            val frame = root ?: return
            val lp = params ?: return
            box = next
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
        const val MIN_WIDTH_DP = 232
        const val MIN_HEIGHT_DP = 330
        const val TITLE_BAR_DP = 44
        const val CLOSE_ZONE_DP = 56
        const val GRIP_DP = 36
    }
}
