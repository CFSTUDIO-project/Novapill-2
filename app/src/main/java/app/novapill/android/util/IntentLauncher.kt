package app.novapill.android.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Opening other apps from the overlay. Every call is guarded: a failure is reported, never thrown. */
object IntentLauncher {
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Sends a PendingIntent that belongs to another app (media "open player", notification content).
     * Android 14+ requires the sender to opt in to background activity starts; the user just tapped
     * a visible overlay, so this is an explicit, user-initiated action.
     */
    fun send(context: Context, pending: PendingIntent?): Boolean {
        if (pending == null) return false
        return try {
            val options = if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            pending.send(context, 0, null, null, null, null, options)
            true
        } catch (_: Exception) {
            false
        }
    }
}
