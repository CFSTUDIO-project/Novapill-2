package app.novapill.android.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.novapill.android.NovaGraph

/**
 * Bound by the system once the user grants notification access. It does two jobs:
 *  - gives MediaSessionManager the component it needs to list active media sessions, and
 *  - forwards posted/removed notifications to the hub, which decides whether anything is shown.
 * Nothing read here is stored or sent anywhere.
 */
class NovaNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerBridge.onConnected()
        try {
            NovaGraph.media.onListenerConnected()
        } catch (e: Exception) {
            NovaGraph.diagnostics.error("listener", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ListenerBridge.onDisconnected()
        try {
            NovaGraph.media.onListenerDisconnected()
        } catch (e: Exception) {
            NovaGraph.diagnostics.error("listener", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            NovaGraph.notifications.onPosted(sbn)
        } catch (e: Exception) {
            NovaGraph.diagnostics.error("notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        try {
            NovaGraph.notifications.onRemoved(sbn)
        } catch (e: Exception) {
            NovaGraph.diagnostics.error("notification", e)
        }
    }
}
