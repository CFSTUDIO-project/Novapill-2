package app.novapill.android.util

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import app.novapill.android.core.TorchConfirm
import app.novapill.android.core.TorchPending
import app.novapill.android.core.TorchStatus
import app.novapill.android.core.TorchTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TorchError { NOT_CONFIRMED, IN_USE, REFUSED, FAILED }

enum class TorchResult { SENT, NO_FLASH, BUSY, REFUSED, FAILED }

/**
 * Everything the UI may know about the torch. [status] is the hardware truth as reported by the system;
 * [pending] only says that a command is on its way and never turns the label to "On" by itself.
 */
data class TorchState(
    val status: TorchStatus,
    val pending: TorchPending = TorchPending.NONE,
    val error: TorchError? = null,
    /** 1 means the phone has no brightness control for the torch (needs Android 13 and hardware support). */
    val maxLevel: Int = 1,
) {
    val on: Boolean get() = status == TorchStatus.ON
    val available: Boolean get() = status != TorchStatus.NO_FLASH
    val adjustable: Boolean get() = maxLevel > 1

    companion object {
        val NONE = TorchState(TorchStatus.NO_FLASH)
        val UNKNOWN = TorchState(TorchStatus.UNKNOWN)
    }
}

/**
 * The torch, driven by CameraManager only (no camera permission, no camera session).
 *
 * Truth model: the state shown to the user is what [CameraManager.TorchCallback] reported, per camera id,
 * aggregated by [TorchTracker]. A tap sends a command and waits for the matching report; if the system does
 * not confirm within [CONFIRM_TIMEOUT_MS] the tap is reported as "not confirmed" and the label does not
 * change. Nothing is On by default, the callback is registered only between [start] and [stop], and a
 * torch this app switched on is switched off again by [releaseIfOwned].
 */
class Flashlight(private val context: Context, private val diagnostics: Diagnostics) {
    private class FlashUnit(val id: String, val back: Boolean, val maxLevel: Int)

    private val manager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = TorchTracker()

    val hasFlash: Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private var units: List<FlashUnit> = emptyList()
    private var callback: CameraManager.TorchCallback? = null
    private var pending = TorchPending.NONE
    private var error: TorchError? = null
    private var ownedOn = false
    private var timeout: Runnable? = null

    private val _state = MutableStateFlow(if (hasFlash) TorchState.UNKNOWN else TorchState.NONE)
    val state: StateFlow<TorchState> = _state.asStateFlow()

    /** Registers for torch reports. The system answers immediately with the current state of every flash unit. */
    fun start() {
        val m = manager ?: return
        if (!hasFlash || callback != null) return
        units = discover(m)
        tracker.reset()
        for (u in units) tracker.track(u.id)
        pending = TorchPending.NONE
        error = null
        register(m)
        publish()
    }

    /** Unregisters, forgets everything and leaves the label neutral until the next [start]. */
    fun stop() {
        cancelTimeout()
        val m = manager
        val cb = callback
        if (m != null && cb != null) {
            try {
                m.unregisterTorchCallback(cb)
            } catch (e: IllegalArgumentException) {
                diagnostics.error("flash", e)
            }
        }
        callback = null
        tracker.reset()
        pending = TorchPending.NONE
        error = null
        publish()
    }

    /** Registers a fresh callback so the system replays the real state (used when the panel opens). */
    fun resync() {
        val m = manager ?: return
        if (callback == null) {
            start()
            return
        }
        val old = callback
        if (old != null) {
            try {
                m.unregisterTorchCallback(old)
            } catch (e: IllegalArgumentException) {
                diagnostics.error("flash", e)
            }
        }
        register(m)
    }

    /** Switches off a torch that this app switched on, so stopping the island never leaves it lit. */
    fun releaseIfOwned() {
        if (ownedOn && tracker.status() == TorchStatus.ON) sendOff(manager ?: return)
        ownedOn = false
    }

    /** Toggles according to the reported state. [percent] only matters on phones with brightness control. */
    fun toggle(percent: Int): TorchResult {
        val m = manager
        if (m == null || !hasFlash) return TorchResult.NO_FLASH
        if (callback == null) start()
        if (units.isEmpty()) return TorchResult.NO_FLASH
        return when (tracker.status()) {
            TorchStatus.ON -> sendOff(m)
            TorchStatus.BUSY -> {
                error = TorchError.IN_USE
                publish()
                TorchResult.BUSY
            }
            else -> sendOn(m, percent)
        }
    }

    // ---- commands ----

    private fun sendOn(m: CameraManager, percent: Int): TorchResult {
        val order = units.sortedByDescending { it.back }.map { it.id }
        val id = tracker.pickForOn(order) ?: return TorchResult.BUSY
        val unit = units.firstOrNull { it.id == id }
        return try {
            if (Build.VERSION.SDK_INT >= 33 && unit != null && unit.maxLevel > 1 && percent < 100) {
                m.turnOnTorchWithStrengthLevel(id, TorchConfirm.levelFor(percent, unit.maxLevel))
            } else {
                m.setTorchMode(id, true)
            }
            ownedOn = true
            begin(TorchPending.TO_ON)
            TorchResult.SENT
        } catch (e: CameraAccessException) {
            failed(e)
        } catch (e: IllegalArgumentException) {
            failed(e)
        } catch (e: SecurityException) {
            failed(e)
        }
    }

    private fun sendOff(m: CameraManager): TorchResult {
        val ids = tracker.idsOn().ifEmpty { units.map { it.id } }
        var worst: TorchResult? = null
        for (id in ids) {
            try {
                m.setTorchMode(id, false)
            } catch (e: CameraAccessException) {
                worst = failed(e)
            } catch (e: IllegalArgumentException) {
                worst = failed(e)
            } catch (e: SecurityException) {
                worst = failed(e)
            }
        }
        if (worst != null) return worst
        ownedOn = false
        begin(TorchPending.TO_OFF)
        return TorchResult.SENT
    }

    private fun failed(e: Exception): TorchResult {
        diagnostics.error("flash", e)
        val inUse = e is CameraAccessException &&
            (e.reason == CameraAccessException.CAMERA_IN_USE || e.reason == CameraAccessException.MAX_CAMERAS_IN_USE)
        pending = TorchPending.NONE
        cancelTimeout()
        error = if (inUse) TorchError.IN_USE else if (e is IllegalArgumentException) TorchError.FAILED else TorchError.REFUSED
        publish()
        return if (inUse) TorchResult.BUSY else if (e is IllegalArgumentException) TorchResult.FAILED else TorchResult.REFUSED
    }

    private fun begin(p: TorchPending) {
        pending = p
        error = null
        cancelTimeout()
        val task = Runnable {
            timeout = null
            if (pending != TorchPending.NONE) {
                pending = TorchPending.NONE
                error = TorchError.NOT_CONFIRMED
                publish()
            }
        }
        timeout = task
        handler.postDelayed(task, CONFIRM_TIMEOUT_MS)
        publish()
    }

    private fun cancelTimeout() {
        timeout?.let { handler.removeCallbacks(it) }
        timeout = null
    }

    // ---- reports ----

    private fun register(m: CameraManager) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                tracker.onChanged(cameraId, enabled)
                settle()
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                tracker.onUnavailable(cameraId)
                settle()
            }
        }
        callback = cb
        try {
            m.registerTorchCallback(cb, handler)
        } catch (e: RuntimeException) {
            diagnostics.error("flash", e)
            callback = null
            error = TorchError.FAILED
            publish()
        }
    }

    /** A report arrived: resolve a waiting command if this report proves it, then publish the new truth. */
    private fun settle() {
        val status = tracker.status()
        if (status != TorchStatus.ON) ownedOn = false
        if (TorchConfirm.confirmed(pending, status)) {
            pending = TorchPending.NONE
            error = null
            cancelTimeout()
        } else if (status == TorchStatus.ON || status == TorchStatus.OFF) {
            // A real report supersedes an old failure message.
            if (pending == TorchPending.NONE) error = null
        }
        publish()
    }

    private fun publish() {
        val status = when {
            !hasFlash || (callback != null && units.isEmpty()) -> TorchStatus.NO_FLASH
            callback == null -> TorchStatus.UNKNOWN
            else -> tracker.status()
        }
        val level = units.firstOrNull { it.back }?.maxLevel ?: units.firstOrNull()?.maxLevel ?: 1
        _state.value = TorchState(status, pending, error, level)
    }

    private fun discover(m: CameraManager): List<FlashUnit> {
        val out = ArrayList<FlashUnit>()
        val ids = try {
            m.cameraIdList
        } catch (e: CameraAccessException) {
            diagnostics.error("flash", e)
            return out
        }
        for (id in ids) {
            try {
                val ch = m.getCameraCharacteristics(id)
                if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
                val back = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                val max = if (Build.VERSION.SDK_INT >= 33) {
                    ch.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                } else {
                    1
                }
                out.add(FlashUnit(id, back, max))
            } catch (e: CameraAccessException) {
                diagnostics.error("flash", e)
            } catch (e: IllegalArgumentException) {
                diagnostics.error("flash", e)
            }
        }
        return out
    }

    private companion object {
        const val CONFIRM_TIMEOUT_MS = 1800L
    }
}
