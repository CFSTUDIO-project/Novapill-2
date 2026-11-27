package app.novapill.android.util

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class VolumeLevel(val current: Int, val max: Int) {
    val fraction: Float get() = if (max <= 0) 0f else (current.toFloat() / max.toFloat()).coerceIn(0f, 1f)
}

/** Media-stream volume through AudioManager. It never changes the volume unless the user drags the slider. */
class VolumeController(private val context: Context) {
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    private val _level = MutableStateFlow(read())
    val level: StateFlow<VolumeLevel> = _level.asStateFlow()

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    fun start() {
        if (observing) return
        try {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            observing = true
        } catch (_: Exception) {
            // the slider still works, it just refreshes when the card is redrawn
        }
        refresh()
    }

    fun stop() {
        if (!observing) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
            // already gone
        }
        observing = false
    }

    fun refresh() {
        _level.value = read()
    }

    /** Sets the media volume to [fraction] of the device maximum; the system still enforces its own limits. */
    fun set(fraction: Float) {
        val am = audio ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (fraction.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: SecurityException) {
            // some devices refuse changes while a policy such as Do Not Disturb is active
        }
        refresh()
    }

    private fun read(): VolumeLevel {
        val am = audio ?: return VolumeLevel(0, 1)
        return VolumeLevel(am.getStreamVolume(AudioManager.STREAM_MUSIC), am.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
}
