package app.novapill.android.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.novapill.android.MainActivity
import app.novapill.android.NovaGraph
import app.novapill.android.R
import app.novapill.android.ServiceStatus
import app.novapill.android.overlay.IslandCoordinator
import app.novapill.android.overlay.IslandWindowController
import app.novapill.android.perm.Permissions

/**
 * Foreground service (type specialUse) that owns the overlay window while the user has the island on.
 * Starting is always an explicit action (the START button, or restoring what the user left running);
 * Stop removes the window, the engines' callbacks and the notification completely.
 */
class IslandService : Service() {
    private var coordinator: IslandCoordinator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopIsland(userRequested = true)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!startIsland()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        coordinator?.onConfigurationChanged()
    }

    override fun onDestroy() {
        // Killed or stopped by the system: tear down, but keep "island enabled" so it can be restored.
        stopIsland(userRequested = false)
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        NotificationChannels.ensure(this)
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, IslandService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannels.ISLAND)
            .setSmallIcon(R.drawable.ic_stat_nova)
            .setContentTitle(getString(R.string.notif_island_title))
            .setContentText(getString(R.string.notif_island_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            fail("Android did not allow the service to start (${e.javaClass.simpleName}).")
            false
        }
    }

    private fun startIsland(): Boolean {
        if (coordinator != null) return true
        if (!Permissions.canDrawOverlays(this)) {
            fail("Display over other apps is not allowed.")
            return false
        }
        val window = IslandWindowController(this)
        val c = IslandCoordinator(this, window)
        NovaGraph.system.start()
        NovaGraph.battery.start()
        NovaGraph.volume.start()
        NovaGraph.record.start()
        NovaGraph.flashlight.start()
        if (!c.start()) {
            stopEngines()
            fail("The overlay could not be added${window.lastError?.let { ": $it" } ?: "."}")
            return false
        }
        coordinator = c
        NovaGraph.settings.update { it.copy(islandEnabled = true) }
        NovaGraph.setServiceStatus(ServiceStatus.RUNNING)
        return true
    }

    private fun stopIsland(userRequested: Boolean) {
        val had = coordinator != null
        coordinator?.stop()
        coordinator = null
        if (had) stopEngines()
        if (userRequested) NovaGraph.settings.update { it.copy(islandEnabled = false) }
        if (NovaGraph.serviceStatus.value != ServiceStatus.FAILED || userRequested) {
            NovaGraph.setServiceStatus(ServiceStatus.STOPPED)
        }
    }

    private fun stopEngines() {
        NovaGraph.system.stop()
        NovaGraph.battery.stop()
        NovaGraph.volume.stop()
        NovaGraph.record.stop()
        NovaGraph.flashlight.releaseIfOwned()
        NovaGraph.flashlight.stop()
    }

    private fun fail(message: String) {
        NovaGraph.diagnostics.note("service", message)
        NovaGraph.setServiceStatus(ServiceStatus.FAILED, message)
    }

    companion object {
        const val ACTION_START = "app.novapill.android.action.START_ISLAND"
        const val ACTION_STOP = "app.novapill.android.action.STOP_ISLAND"
        private const val NOTIFICATION_ID = 4100

        /** Starts the island. Must be called while the app is visible, or from a boot broadcast. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IslandService::class.java).setAction(ACTION_START))
        }

        /** Stops the island for good: the overlay disappears and the "enabled" setting is turned off. */
        fun stop(context: Context) {
            context.startService(Intent(context, IslandService::class.java).setAction(ACTION_STOP))
        }
    }
}
