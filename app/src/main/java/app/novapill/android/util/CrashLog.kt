package app.novapill.android.util

import java.io.File

/**
 * A small local crash log. When the app dies from an uncaught exception, the class, a short message and the top
 * frames of the stack are appended to a file in the app's private storage, then Android's normal handling goes on.
 * Nothing is sent anywhere; the About screen shows the file so a bug report can quote it. Only code locations
 * and the exception's own message are written, never notification, lyrics or note text.
 */
class CrashLog(private val file: File) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                append(format(thread.name, error, System.currentTimeMillis()))
            } catch (_: Throwable) {
                // the log must never be the reason a crash gets worse
            }
            previous?.uncaughtException(thread, error)
        }
    }

    @Synchronized
    fun append(entry: String) {
        val old = if (file.exists()) file.readText() else ""
        file.writeText(trim(old + entry))
    }

    @Synchronized
    fun read(): String = if (file.exists()) file.readText() else ""

    @Synchronized
    fun clear() {
        file.delete()
    }

    companion object {
        const val MAX_CHARS = 16_000
        private const val MAX_FRAMES = 14

        fun format(threadName: String, error: Throwable, atMs: Long): String {
            val sb = StringBuilder()
            sb.append("--- ").append(atMs).append(" thread=").append(threadName).append('\n')
            var t: Throwable? = error
            var depth = 0
            while (t != null && depth < 3) {
                sb.append(if (depth == 0) "" else "caused by ").append(t.javaClass.name)
                val msg = t.message?.replace('\n', ' ')?.take(160)
                if (!msg.isNullOrEmpty()) sb.append(": ").append(msg)
                sb.append('\n')
                val frames = t.stackTrace
                for (i in 0 until minOf(frames.size, MAX_FRAMES)) {
                    sb.append("    at ").append(frames[i].className).append('.').append(frames[i].methodName)
                    sb.append('(').append(frames[i].fileName ?: "?").append(':').append(frames[i].lineNumber).append(")\n")
                }
                t = t.cause?.takeIf { it !== t }
                depth++
            }
            return sb.toString()
        }

        /** Keeps the newest entries: when the text is too long, whole old entries are dropped from the front. */
        fun trim(text: String): String {
            if (text.length <= MAX_CHARS) return text
            val cut = text.length - MAX_CHARS
            val next = text.indexOf("--- ", cut)
            return if (next >= 0) text.substring(next) else text.takeLast(MAX_CHARS)
        }
    }
}
