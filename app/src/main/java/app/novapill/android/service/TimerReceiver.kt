package app.novapill.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.novapill.android.NovaGraph
import app.novapill.android.core.TimerPhase
import app.novapill.android.util.ClockTools

/** Backup for the in-process timer: fires from AlarmManager when the countdown should be over. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FINISHED) return
        val clocks = NovaGraph.clocks
        if (clocks.timer.value.phase == TimerPhase.NONE) {
            // The process was recreated just for this broadcast, so the countdown state is gone,
            // but the user still has to be told their timer finished.
            ClockTools.postFinished(context)
        } else {
            clocks.onFinished()
        }
    }

    companion object {
        const val ACTION_FINISHED = "app.novapill.android.TIMER_FINISHED"
    }
}
