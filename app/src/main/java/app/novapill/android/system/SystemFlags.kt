package app.novapill.android.system

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Screen state, battery saver and motion preferences that decide whether the island animates at all. */
class SystemFlags(private val context: Context) {
    private val power = context.getSystemService(PowerManager::class.java)

    private val _screenOn = MutableStateFlow(true)
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val _powerSave = MutableStateFlow(false)
    val powerSave: StateFlow<Boolean> = _powerSave.asStateFlow()

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> _screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> _screenOn.value = false
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> _powerSave.value = power?.isPowerSaveMode ?: false
            }
        }
    }

    fun start() {
        if (registered) return
        _screenOn.value = power?.isInteractive ?: true
        _powerSave.value = power?.isPowerSaveMode ?: false
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registered = true
    }

    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
        registered = false
    }

    /** True when the user turned system animations off ("Remove animations"). */
    fun systemAnimationsOff(): Boolean = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (_: Exception) {
        false
    }

    fun isKeyguardLocked(): Boolean = try {
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
    } catch (_: Exception) {
        false
    }
}
