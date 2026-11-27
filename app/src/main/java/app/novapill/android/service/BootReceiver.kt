package app.novapill.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.novapill.android.NovaGraph
import app.novapill.android.perm.Permissions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Optional "start after reboot". It only restarts an island the user had running, only when the
 * user turned this option on, and only when the overlay permission is still granted. Some phone makers
 * block boot-time starts; in that case nothing happens and the user simply taps START.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        NovaGraph.appScope.launch {
            try {
                withTimeoutOrNull(4_000L) { NovaGraph.settings.loaded.first { it } }
                val s = NovaGraph.settings.settings.value
                if (s.autoStartOnBoot && s.islandEnabled && Permissions.canDrawOverlays(context)) {
                    IslandService.start(context)
                }
            } catch (e: Exception) {
                NovaGraph.diagnostics.error("boot", e)
            } finally {
                pending.finish()
            }
        }
    }
}
