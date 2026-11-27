package app.novapill.android.service

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Connection state of the notification listener, shared between the system-bound service and the app. */
object ListenerBridge {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun onConnected() {
        _connected.value = true
    }

    fun onDisconnected() {
        _connected.value = false
    }

    fun component(context: Context): ComponentName = ComponentName(context, NovaNotificationListener::class.java)
}
