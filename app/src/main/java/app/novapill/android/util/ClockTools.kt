package app.novapill.android.util

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.novapill.android.MainActivity
import app.novapill.android.R
import app.novapill.android.core.CountdownMath
import app.novapill.android.core.CountdownState
import app.novapill.android.core.StopwatchMath
import app.novapill.android.core.StopwatchState
import app.novapill.android.core.TimerPhase
import app.novapill.android.service.NotificationChannels
import app.novapill.android.service.TimerReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Countdown timer and stopwatch. They live in the process, not in the island window, so closing the
 * expanded panel (or hiding the island) never loses them. Time comes from SystemClock.elapsedRealtime.
 *
 * A finished timer is announced by a notification; an inexact, Doze-friendly alarm backs up the in-process
 * coroutine so the notification still appears if the process is frozen (it can then arrive a little late).
 * The running countdown itself is not restored if Android kills the process.
 */
class ClockTools(
    private val context: Context,
    private val scope: CoroutineScope,
    private val diagnostics: Diagnostics,
) {
    private val _timer = MutableStateFlow(CountdownState())
    val timer: StateFlow<CountdownState> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchState())
    val stopwatch: StateFlow<StopwatchState> = _stopwatch.asStateFlow()

    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private var finishJob: Job? = null

    fun now(): Long = SystemClock.elapsedRealtime()

    // ---- timer ----

    fun startTimer(totalMs: Long) {
        dismissNotification(context)
        _timer.value = CountdownMath.start(totalMs, now())
        schedule()
    }

    fun pauseTimer() {
        _timer.value = CountdownMath.pause(_timer.value, now())
        schedule()
    }

    fun resumeTimer() {
        _timer.value = CountdownMath.resume(_timer.value, now())
        schedule()
    }

    fun addTime(extraMs: Long) {
        dismissNotification(context)
        _timer.value = CountdownMath.addTime(_timer.value, extraMs, now())
        schedule()
    }

    fun cancelTimer() {
        _timer.value = CountdownMath.cancel()
        schedule()
        dismissNotification(context)
    }

    /** Moves RUNNING to FINISHED when the end instant has passed; safe to call any number of times. */
    fun onFinished() {
        val before = _timer.value
        val after = CountdownMath.tick(before, now())
        if (before.phase == TimerPhase.RUNNING && after.phase == TimerPhase.FINISHED) {
            _timer.value = after
            cancelAlarm()
            postFinished(context)
        }
    }

    private fun schedule() {
        finishJob?.cancel()
        finishJob = null
        cancelAlarm()
        val s = _timer.value
        if (s.phase != TimerPhase.RUNNING) return
        try {
            alarms?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, s.endsAtMs, alarmIntent(context))
        } catch (e: Exception) {
            diagnostics.error("timer", e)
        }
        finishJob = scope.launch {
            while (true) {
                val remaining = _timer.value.endsAtMs - now()
                if (remaining <= 0L) break
                delay(remaining)
            }
            onFinished()
        }
    }

    private fun cancelAlarm() {
        try {
            alarms?.cancel(alarmIntent(context))
        } catch (_: Exception) {
            // nothing scheduled
        }
    }

    // ---- stopwatch ----

    fun stopwatchStart() {
        _stopwatch.value = StopwatchMath.start(now())
    }

    fun stopwatchPause() {
        _stopwatch.value = StopwatchMath.pause(_stopwatch.value, now())
    }

    fun stopwatchResume() {
        _stopwatch.value = StopwatchMath.resume(_stopwatch.value, now())
    }

    fun stopwatchReset() {
        _stopwatch.value = StopwatchMath.reset()
    }

    companion object {
        private const val ALARM_REQUEST = 4101
        private const val NOTIFICATION_ID = 4102

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST,
            Intent(context, TimerReceiver::class.java).setAction(TimerReceiver.ACTION_FINISHED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        @SuppressLint("MissingPermission")
        fun postFinished(context: Context) {
            NotificationChannels.ensure(context)
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.TIMER)
                .setSmallIcon(R.drawable.ic_stat_nova)
                .setContentTitle(context.getString(R.string.notif_timer_title))
                .setContentText(context.getString(R.string.notif_timer_text))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // notifications not allowed: the island card still shows the finished state
            }
        }

        fun dismissNotification(context: Context) {
            try {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            } catch (_: Exception) {
                // nothing posted
            }
        }
    }
}
