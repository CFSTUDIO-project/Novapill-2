package app.novapill.android.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import app.novapill.android.data.SettingsRepository
import app.novapill.android.perm.Permissions
import app.novapill.android.system.SystemFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Knows which app is in the foreground, but only to implement "Hide in selected apps", and only while
 * the user has chosen apps and granted Usage access. Polling is slow (1.5 s), runs only while the screen
 * is on, and stops completely when the list is empty.
 */
class ForegroundAppMonitor(
    private val context: Context,
    private val settings: SettingsRepository,
    private val system: SystemFlags,
) {
    private val _foreground = MutableStateFlow<String?>(null)
    val foreground: StateFlow<String?> = _foreground.asStateFlow()

    private var job: Job? = null
    private var lastQuery = 0L

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                val wanted = settings.settings.value.hiddenApps.isNotEmpty() && system.screenOn.value && Permissions.usageAccess(context)
                if (wanted) {
                    poll()
                    delay(POLL_MS)
                } else {
                    _foreground.value = null
                    lastQuery = 0L
                    delay(IDLE_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _foreground.value = null
        lastQuery = 0L
    }

    private fun poll() {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val begin = if (lastQuery == 0L) now - 60_000L else lastQuery - 1_000L
        try {
            val events = usm.queryEvents(begin, now)
            val event = UsageEvents.Event()
            var latest: String? = null
            var latestTime = 0L
            val resumed = if (Build.VERSION.SDK_INT >= 29) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == resumed && event.timeStamp >= latestTime) {
                    latest = event.packageName
                    latestTime = event.timeStamp
                }
            }
            lastQuery = now
            if (latest != null) _foreground.value = latest
        } catch (_: Exception) {
            // usage access revoked mid-poll: the next loop notices
        }
    }

    private companion object {
        const val POLL_MS = 1_500L
        const val IDLE_MS = 3_000L
    }
}
