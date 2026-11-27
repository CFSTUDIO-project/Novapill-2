package app.novapill.android.notif

import app.novapill.android.settings.PreviewPrivacy

/**
 * What a peek shows about one message. Privacy has already been applied: a field the user chose to hide is
 * left at its placeholder or null, so the UI never has to second-guess the setting.
 */
data class MessageLines(
    val app: String,
    val sender: String,
    /** The message text, or null when the privacy level does not allow it. */
    val body: String?,
)

/** Pure text work for peeks: cleaning, privacy and length limits. No Android types. */
object NotificationText {
    const val GENERIC = "New notification"
    const val APP_MAX = 40
    const val SENDER_MAX = 32
    const val BODY_MAX = 120

    /** Collapses whitespace and control characters, drops invisible direction marks. */
    fun clean(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        for (ch in raw) {
            if (isInvisible(ch)) continue
            if (ch.isWhitespace() || ch.isISOControl()) {
                pendingSpace = true
                continue
            }
            if (pendingSpace && out.isNotEmpty()) out.append(' ')
            pendingSpace = false
            out.append(ch)
        }
        return out.toString()
    }

    /** Shortens to [max] characters with an ellipsis, never splitting a surrogate pair. */
    fun cap(s: String, max: Int): String {
        require(max >= 2) { "max must leave room for the ellipsis" }
        if (s.length <= max) return s
        var end = max - 1
        if (end > 0 && Character.isHighSurrogate(s[end - 1])) end--
        return s.substring(0, end).trimEnd() + "…"
    }

    /**
     * Builds the lines for one notification.
     * [sender] is the messaging-style sender when there is one; otherwise the title stands in for it.
     */
    fun lines(
        app: String,
        sender: String?,
        title: String?,
        body: String?,
        privacy: PreviewPrivacy,
        locked: Boolean,
        secret: Boolean,
    ): MessageLines {
        val appName = cap(clean(app).ifEmpty { GENERIC }, APP_MAX)
        val level = if (locked || secret) PreviewPrivacy.APP_ONLY else privacy
        if (level == PreviewPrivacy.APP_ONLY) return MessageLines(appName, GENERIC, null)

        val who = clean(sender).ifEmpty { clean(title) }.ifEmpty { GENERIC }
        if (level == PreviewPrivacy.SUMMARY) return MessageLines(appName, cap(who, SENDER_MAX), null)

        val text = clean(body)
        val shown = if (text.isEmpty() || text == who) null else cap(text, BODY_MAX)
        return MessageLines(appName, cap(who, SENDER_MAX), shown)
    }

    private fun isInvisible(ch: Char): Boolean = when (ch) {
        '​', '‎', '‏', '‪', '‫', '‬', '‭', '‮',
        '⁦', '⁧', '⁨', '⁩', '﻿' -> true
        else -> false
    }
}

/**
 * Remembers the last content shown for each notification key. A re-post with the same content (a progress
 * refresh, a re-sent identical message) does not peek a second time. Bounded, so it cannot grow forever.
 */
class NotificationDedup(private val capacity: Int = 64) {
    private val last = object : LinkedHashMap<String, Long>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > capacity
    }

    /** True when [fingerprint] differs from what was last seen for [key]; remembers it either way. */
    fun isNew(key: String, fingerprint: Long): Boolean = last.put(key, fingerprint) != fingerprint

    fun forget(key: String) {
        last.remove(key)
    }

    fun clear() {
        last.clear()
    }

    fun size(): Int = last.size

    companion object {
        private const val FNV_OFFSET = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L

        /** 64-bit FNV-1a over the given parts; a separator keeps ("ab","c") apart from ("a","bc"). */
        fun fingerprint(vararg parts: String?): Long {
            var h = FNV_OFFSET
            for (part in parts) {
                for (ch in part.orEmpty()) {
                    h = (h xor ch.code.toLong()) * FNV_PRIME
                }
                h = (h xor SEPARATOR) * FNV_PRIME
            }
            return h
        }

        private const val SEPARATOR = 0x1FL
    }
}
