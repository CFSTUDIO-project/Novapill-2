package app.novapill.android.core

import java.util.Locale

/** Text formatting that does not depend on the platform locale, so output is predictable in tests. */
object TimeFormat {
    /** 3:07 or 1:03:07. Negative input is treated as zero. */
    fun trackTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** 04:32 for timers; hours appear only when needed. Rounds up so 0.4 s left still shows 00:01. */
    fun countdown(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%02d:%02d".format(Locale.ROOT, m, s)
    }

    /** HH:MM:SS exactly as the record indicator shows it. */
    fun hms(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        return "%02d:%02d:%02d".format(Locale.ROOT, total / 3600, (total % 3600) / 60, total % 60)
    }

    /** 00:12.3 for the stopwatch (tenths, truncated, never rounded up). */
    fun stopwatch(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val totalSec = t / 1000
        val tenths = (t % 1000) / 100
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m >= 60) {
            "%d:%02d:%02d.%d".format(Locale.ROOT, m / 60, m % 60, s, tenths)
        } else {
            "%02d:%02d.%d".format(Locale.ROOT, m, s, tenths)
        }
    }

    /** Clock text from hour (0-23) and minute. */
    fun clock(hour24: Int, minute: Int, use24h: Boolean): String {
        return if (use24h) {
            "%02d:%02d".format(Locale.ROOT, hour24, minute)
        } else {
            val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
            "%d:%02d".format(Locale.ROOT, h12, minute)
        }
    }

    fun meridiem(hour24: Int): String = if (hour24 < 12) "AM" else "PM"

    /** Short minutes label for timer presets: 5 min, 1 h, 1 h 30 min. */
    fun presetLabel(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }
}
