package app.novapill.android.util

enum class WeatherKind { CLEAR_DAY, CLEAR_NIGHT, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER, UNKNOWN }

/** WMO weather interpretation codes as used by Open-Meteo. */
object WeatherCodes {
    fun kind(code: Int, isDay: Boolean): WeatherKind = when (code) {
        0 -> if (isDay) WeatherKind.CLEAR_DAY else WeatherKind.CLEAR_NIGHT
        1, 2 -> WeatherKind.PARTLY_CLOUDY
        3 -> WeatherKind.CLOUDY
        45, 48 -> WeatherKind.FOG
        51, 53, 55, 56, 57 -> WeatherKind.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> WeatherKind.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherKind.SNOW
        95, 96, 99 -> WeatherKind.THUNDER
        else -> WeatherKind.UNKNOWN
    }

    fun label(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71 -> "Light snow"
        73 -> "Snow"
        75 -> "Heavy snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, hail"
        else -> "Unknown"
    }

    fun toFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    /** 27° (never "-0°"). */
    fun formatTemp(celsius: Double, fahrenheit: Boolean): String {
        val value = if (fahrenheit) toFahrenheit(celsius) else celsius
        val rounded = Math.round(value).toInt()
        return "$rounded°"
    }
}
