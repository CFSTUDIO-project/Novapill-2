package app.novapill.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {
    @Test
    fun kindsFollowWmoCodes() {
        assertEquals(WeatherKind.CLEAR_DAY, WeatherCodes.kind(0, true))
        assertEquals(WeatherKind.CLEAR_NIGHT, WeatherCodes.kind(0, false))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherCodes.kind(2, true))
        assertEquals(WeatherKind.CLOUDY, WeatherCodes.kind(3, true))
        assertEquals(WeatherKind.FOG, WeatherCodes.kind(45, true))
        assertEquals(WeatherKind.DRIZZLE, WeatherCodes.kind(53, true))
        assertEquals(WeatherKind.RAIN, WeatherCodes.kind(63, true))
        assertEquals(WeatherKind.RAIN, WeatherCodes.kind(81, true))
        assertEquals(WeatherKind.SNOW, WeatherCodes.kind(75, true))
        assertEquals(WeatherKind.THUNDER, WeatherCodes.kind(95, true))
        assertEquals(WeatherKind.UNKNOWN, WeatherCodes.kind(1234, true))
    }

    @Test
    fun labelsAreHumanReadable() {
        assertEquals("Clear", WeatherCodes.label(0))
        assertEquals("Thunderstorm", WeatherCodes.label(95))
        assertEquals("Unknown", WeatherCodes.label(-1))
    }

    @Test
    fun temperatureFormattingNeverShowsNegativeZero() {
        assertEquals("27°", WeatherCodes.formatTemp(26.6, false))
        assertEquals("0°", WeatherCodes.formatTemp(-0.4, false))
        assertEquals("32°", WeatherCodes.formatTemp(0.0, true))
        assertEquals("-5°", WeatherCodes.formatTemp(-5.2, false))
    }
}
