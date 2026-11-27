package app.novapill.android.notif

import android.app.PendingIntent
import android.app.RemoteInput

/**
 * Keeps the reply action of recent notifications so the quick-reply screen can send through it. Only the
 * action's PendingIntent and its input description are held, never any message text, and the list is bounded
 * and in memory. Everything runs on the main thread (the listener and the UI both do).
 */
object ReplyRegistry {
    class Target(val pending: PendingIntent, val inputs: Array<RemoteInput>, val resultKey: String)

    private const val CAPACITY = 24

    private val targets = object : LinkedHashMap<String, Target>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Target>?): Boolean = size > CAPACITY
    }

    fun put(key: String, target: Target) {
        targets[key] = target
    }

    fun get(key: String): Target? = targets[key]

    fun remove(key: String) {
        targets.remove(key)
    }

    fun clear() {
        targets.clear()
    }

    /** The first action of [actions] that takes free text, or null when the notification offers no reply. */
    fun findReply(actions: Array<android.app.Notification.Action>?): Target? {
        if (actions == null) return null
        for (action in actions) {
            val pending = action.actionIntent ?: continue
            val inputs = action.remoteInputs ?: continue
            val free = inputs.firstOrNull { it.allowFreeFormInput } ?: continue
            return Target(pending, inputs, free.resultKey)
        }
        return null
    }
}
