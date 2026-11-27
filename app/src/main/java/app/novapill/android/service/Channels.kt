package app.novapill.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import app.novapill.android.R

object NotificationChannels {
    const val ISLAND = "nova_island"
    const val TIMER = "nova_timer"
    const val RECORDING = "nova_recording"

    fun ensure(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val island = NotificationChannel(ISLAND, context.getString(R.string.channel_island), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_island_desc)
            setShowBadge(false)
        }
        val timer = NotificationChannel(TIMER, context.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_timer_desc)
            enableVibration(true)
        }
        val recording = NotificationChannel(RECORDING, context.getString(R.string.channel_recording), NotificationManager.IMPORTANCE_LOW).apply {
            description = context.getString(R.string.channel_recording_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(island)
        nm.createNotificationChannel(timer)
        nm.createNotificationChannel(recording)
    }
}
