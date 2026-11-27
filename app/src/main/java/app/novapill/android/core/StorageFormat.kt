package app.novapill.android.core

import java.util.Locale

/** Human-readable sizes, decimal units like the Android storage screen ("12.4 GB"). */
object StorageFormat {
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    fun bytes(count: Long): String {
        if (count < 0L) return "–"
        var value = count.toDouble()
        var unit = 0
        while (value >= 1000.0 && unit < UNITS.lastIndex) {
            value /= 1000.0
            unit++
        }
        var text = render(value, unit)
        // 999.96 MB would render as "1000 MB": show it as 1.0 GB instead.
        if (text == "1000" && unit < UNITS.lastIndex) {
            value /= 1000.0
            unit++
            text = render(value, unit)
        }
        return "$text ${UNITS[unit]}"
    }

    private fun render(value: Double, unit: Int): String =
        if (unit == 0 || value >= 100.0) Math.round(value).toString() else String.format(Locale.US, "%.1f", value)
}
