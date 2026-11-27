package app.novapill.android.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.WindowManager
import androidx.annotation.RequiresApi
import app.novapill.android.core.RecordClockState
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchPhase
import app.novapill.android.core.StopwatchState
import app.novapill.android.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.function.Consumer

/** [APP] is NOVA PILL's own screen recorder; it is not an indicator source, it is a real recording. */
enum class RecordMethod { OS, MANUAL, UNSUPPORTED, APP }

/**
 * Two honest sources for the red indicator:
 *  - OS: on Android 15+ the system reports when this app's own windows are visible in a screen recording.
 *    It is not a universal "someone is recording" signal and says nothing about other apps.
 *  - Manual: a timer the user starts, pauses, stops and resets by hand.
 * Nothing else is probed: no accessibility service, no root, no Shizuku, no notification polling.
 */
class RecordIndicator(
    private val context: Context,
    private val settings: SettingsRepository,
    private val diagnostics: Diagnostics,
) {
    val osSupported: Boolean = Build.VERSION.SDK_INT >= 35

    private val _osVisible = MutableStateFlow(false)
    val osVisible: StateFlow<Boolean> = _osVisible.asStateFlow()

    /** Monotonic instant at which the OS signal was first observed; counting starts here. */
    private val _osSince = MutableStateFlow(0L)
    val osSince: StateFlow<Long> = _osSince.asStateFlow()

    private val _manual = MutableStateFlow<RecordClockState>(StopwatchState())
    val manual: StateFlow<RecordClockState> = _manual.asStateFlow()

    private var kept = 0L
    private var detector: OsDetector? = null

    @SuppressLint("NewApi")
    fun start() {
        if (detector != null || !osSupported) return
        try {
            val d = OsDetector(context) { visible -> onOs(visible) }
            d.start()
            detector = d
        } catch (e: Exception) {
            diagnostics.error("record", e)
        }
    }

    fun stop() {
        try {
            detector?.stop()
        } catch (_: Exception) {
            // already removed
        }
        detector = null
        _osVisible.value = false
    }

    private fun onOs(visible: Boolean) {
        if (visible && !_osVisible.value) _osSince.value = SystemClock.elapsedRealtime()
        _osVisible.value = visible
    }

    fun method(useOs: Boolean, manualEnabled: Boolean): RecordMethod = when {
        useOs && osSupported -> RecordMethod.OS
        manualEnabled -> RecordMethod.MANUAL
        else -> RecordMethod.UNSUPPORTED
    }

    // ---- manual timer ----

    private fun now(): Long = SystemClock.elapsedRealtime()

    fun manualStart() {
        _manual.value = StopwatchState(StopwatchPhase.RUNNING, now(), kept)
        kept = 0L
    }

    fun manualPause() {
        _manual.value = StopwatchMath.pause(_manual.value, now())
    }

    fun manualResume() {
        _manual.value = StopwatchMath.resume(_manual.value, now())
    }

    /** Ends the indicator. With "reset on stop" off, the elapsed time is kept for the next start. */
    fun manualStop() {
        val resetOnStop = settings.settings.value.recordResetOnStop
        kept = if (resetOnStop) 0L else StopwatchMath.elapsed(_manual.value, now())
        _manual.value = StopwatchMath.reset()
    }

    fun manualReset() {
        kept = 0L
        val cur = _manual.value
        _manual.value = when (cur.phase) {
            StopwatchPhase.RUNNING -> StopwatchMath.start(now())
            StopwatchPhase.PAUSED -> StopwatchState(StopwatchPhase.PAUSED, 0, 0)
            StopwatchPhase.NONE -> cur
        }
    }

    fun manualElapsed(): Long = StopwatchMath.elapsed(_manual.value, now())

    @RequiresApi(35)
    private class OsDetector(private val context: Context, private val onState: (Boolean) -> Unit) {
        private val consumer = Consumer<Int> { state -> onState(state == WindowManager.SCREEN_RECORDING_STATE_VISIBLE) }

        fun start() {
            val wm = context.getSystemService(WindowManager::class.java)
            val initial = wm.addScreenRecordingCallback(context.mainExecutor, consumer)
            onState(initial == WindowManager.SCREEN_RECORDING_STATE_VISIBLE)
        }

        fun stop() {
            context.getSystemService(WindowManager::class.java).removeScreenRecordingCallback(consumer)
        }
    }
}
