package app.novapill.android.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives a [PeekQueue]: shows the current entry for the configured duration, then advances.
 * The timer is cancelled and restarted whenever the visible entry is updated, replaced or touched,
 * and sticky entries (calls) never time out. It never touches media state.
 *
 * All calls are expected on one thread (the main dispatcher in the app, the test dispatcher in tests).
 */
class PeekTimer<T>(
    private val scope: CoroutineScope,
    private val queue: PeekQueue<T>,
    /** Read on every start so a settings change applies to the next peek. */
    private val durationMs: () -> Long,
    private val onChanged: (PeekEntry<T>?) -> Unit,
) {
    private var job: Job? = null
    private var held = false

    val current: PeekEntry<T>? get() = queue.current

    fun offer(entry: PeekEntry<T>, latestOnly: Boolean) {
        val before = queue.current
        val result = queue.offer(entry, latestOnly)
        val after = queue.current
        val visibleChanged = result == OfferResult.UPDATED_CURRENT || after?.key != before?.key
        if (visibleChanged) {
            restart()
            onChanged(after)
        }
    }

    fun remove(key: String) {
        if (queue.remove(key) == RemoveResult.CURRENT_REMOVED) {
            restart()
            onChanged(queue.current)
        }
    }

    /** The time ran out or the user dismissed the visible entry: move on to the next one. */
    fun advance() {
        queue.advance()
        restart()
        onChanged(queue.current)
    }

    /** While the user touches the peek the timer is held; releasing restarts the full duration. */
    fun hold(isHeld: Boolean) {
        held = isHeld
        if (isHeld) {
            job?.cancel()
            job = null
        } else {
            restart()
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        held = false
        queue.clear()
        onChanged(null)
    }

    private fun restart() {
        job?.cancel()
        job = null
        val entry = queue.current ?: return
        if (entry.sticky || held) return
        val ms = durationMs()
        job = scope.launch {
            delay(ms)
            job = null
            advance()
        }
    }
}
