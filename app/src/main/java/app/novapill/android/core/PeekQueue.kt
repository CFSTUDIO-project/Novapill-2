package app.novapill.android.core

/** One queued peek. [payload] carries whatever the UI needs; the queue only looks at the flags. */
data class PeekEntry<T>(
    val key: String,
    /** High priority (calls, alarms) jumps ahead of ordinary notifications. */
    val high: Boolean,
    /** Sticky entries stay until removed (an incoming call), instead of timing out. */
    val sticky: Boolean,
    val payload: T,
)

enum class OfferResult { SHOWN_NOW, UPDATED_CURRENT, UPDATED_QUEUED, PREEMPTED, QUEUED }

enum class RemoveResult { CURRENT_REMOVED, REMOVED, NOT_FOUND }

/**
 * Small ordered queue behind the notification peek. Index 0 is the entry on screen.
 * Pure data structure: timing (2/3/4/6 s) is handled by [PeekTimer].
 */
class PeekQueue<T>(private val maxSize: Int = 6) {
    private val items = ArrayList<PeekEntry<T>>()

    val current: PeekEntry<T>? get() = items.firstOrNull()
    val size: Int get() = items.size
    fun snapshot(): List<PeekEntry<T>> = items.toList()

    fun offer(entry: PeekEntry<T>, latestOnly: Boolean = false): OfferResult {
        val existing = items.indexOfFirst { it.key == entry.key }
        if (existing >= 0) {
            items[existing] = entry
            return if (existing == 0) OfferResult.UPDATED_CURRENT else OfferResult.UPDATED_QUEUED
        }

        if (latestOnly && !entry.high) {
            val keepHigh = items.filter { it.high }
            items.clear()
            items.addAll(keepHigh)
            items.add(entry)
            return if (items.size == 1) OfferResult.SHOWN_NOW else OfferResult.QUEUED
        }

        if (items.isEmpty()) {
            items.add(entry)
            return OfferResult.SHOWN_NOW
        }

        if (entry.high) {
            if (!items[0].high) {
                items.add(0, entry)
                trim()
                return OfferResult.PREEMPTED
            }
            var at = 0
            while (at < items.size && items[at].high) at++
            items.add(at, entry)
            trim()
            return OfferResult.QUEUED
        }

        items.add(entry)
        trim()
        return OfferResult.QUEUED
    }

    /** Drops the on-screen entry and returns the next one (now current), or null when empty. */
    fun advance(): PeekEntry<T>? {
        if (items.isNotEmpty()) items.removeAt(0)
        return items.firstOrNull()
    }

    fun remove(key: String): RemoveResult {
        val index = items.indexOfFirst { it.key == key }
        if (index < 0) return RemoveResult.NOT_FOUND
        items.removeAt(index)
        return if (index == 0) RemoveResult.CURRENT_REMOVED else RemoveResult.REMOVED
    }

    fun clear() = items.clear()

    /** Keep the queue short: drop the oldest ordinary entry that is not on screen. */
    private fun trim() {
        while (items.size > maxSize) {
            val victim = (1 until items.size).firstOrNull { !items[it].high } ?: (items.size - 1)
            items.removeAt(victim)
        }
    }
}
