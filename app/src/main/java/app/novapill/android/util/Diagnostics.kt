package app.novapill.android.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privacy-safe error log: only the area, the exception class and a short system message.
 * Notification text, titles, lyrics or media metadata are never passed in here.
 */
class Diagnostics {
    data class Entry(val atMs: Long, val area: String, val message: String)

    private val _last = MutableStateFlow<Entry?>(null)
    val lastError: StateFlow<Entry?> = _last.asStateFlow()

    fun error(area: String, t: Throwable) {
        val detail = t.message?.replace('\n', ' ')?.take(100).orEmpty()
        _last.value = Entry(System.currentTimeMillis(), area, "${t.javaClass.simpleName}${if (detail.isEmpty()) "" else ": $detail"}")
    }

    fun note(area: String, message: String) {
        _last.value = Entry(System.currentTimeMillis(), area, message.take(140))
    }

    fun clear() {
        _last.value = null
    }
}
