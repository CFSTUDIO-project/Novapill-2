package app.novapill.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Percentage and charging state only. The system gives no honest time estimate, so none is shown. */
data class BatteryInfo(val percent: Int, val charging: Boolean, val known: Boolean)

class BatteryMonitor(private val context: Context) {
    private val _info = MutableStateFlow(from(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))))
    val info: StateFlow<BatteryInfo> = _info.asStateFlow()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            _info.value = from(intent)
        }
    }

    fun start() {
        if (registered) return
        ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
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

    private fun from(intent: Intent?): BatteryInfo {
        if (intent == null) return BatteryInfo(0, false, false)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return BatteryInfo(0, false, false)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryInfo((level * 100) / scale, charging, true)
    }
}
