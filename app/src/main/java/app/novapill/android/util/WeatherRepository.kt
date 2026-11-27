package app.novapill.android.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import app.novapill.android.data.SettingsRepository
import app.novapill.android.perm.Permissions
import app.novapill.android.settings.NovaSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class WeatherInfo(
    val place: String,
    val tempC: Double,
    val code: Int,
    val isDay: Boolean,
    val fetchedAtMs: Long,
    /** Which settings produced this reading, so changing the city invalidates the cache. */
    val source: String,
)

sealed interface WeatherState {
    data object Idle : WeatherState
    data object Loading : WeatherState
    data class Ready(val info: WeatherInfo, val stale: Boolean) : WeatherState
    data class Unavailable(val reason: String) : WeatherState
}

/**
 * Current weather from Open-Meteo (free for non-commercial use under CC BY 4.0, attribution shown in the app).
 * Fetched only while a weather chip is on screen and at most once per refresh interval. When the request
 * fails the last reading is kept and marked stale; nothing is invented.
 */
class WeatherRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val diagnostics: Diagnostics,
) {
    private class Place(val name: String, val lat: Double, val lon: Double)

    private val _state = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var job: Job? = null
    private var last: WeatherInfo? = null
    private var lastAttemptAt = 0L
    private val geocodeCache = HashMap<String, Place>()

    fun refresh(force: Boolean = false) {
        val s = settings.settings.value
        val prev = last?.takeIf { it.source == sourceKey(s) }
        val maxAge = s.weatherRefreshMin * 60_000L
        if (!force && prev != null && System.currentTimeMillis() - prev.fetchedAtMs < maxAge) {
            _state.value = WeatherState.Ready(prev, stale = false)
            return
        }
        if (job?.isActive == true) return
        val attemptAt = System.currentTimeMillis()
        if (!force && attemptAt - lastAttemptAt < MIN_RETRY_MS) return
        lastAttemptAt = attemptAt
        job = scope.launch {
            _state.value = prev?.let { WeatherState.Ready(it, stale = true) } ?: WeatherState.Loading
            _state.value = try {
                load(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.error("weather", e)
                prev?.let { WeatherState.Ready(it, stale = true) } ?: WeatherState.Unavailable("Weather unavailable")
            }
        }
    }

    fun clear() {
        job?.cancel()
        last = null
        geocodeCache.clear()
        _state.value = WeatherState.Idle
    }

    private fun sourceKey(s: NovaSettings): String =
        if (s.weatherUseLocation) "loc:${s.weatherCity.lowercase()}" else "city:${s.weatherCity.lowercase()}"

    private suspend fun load(s: NovaSettings): WeatherState = withContext(Dispatchers.IO) {
        val place = resolvePlace(s) ?: return@withContext WeatherState.Unavailable(
            if (s.weatherUseLocation) "No recent location. Enter a city in Settings." else "Set a city in Settings, Utilities.",
        )
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", "%.2f".format(java.util.Locale.ROOT, place.lat))
            .addQueryParameter("longitude", "%.2f".format(java.util.Locale.ROOT, place.lon))
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("timezone", "auto")
            .build()
        val current = getJson(url).asJsonMap()?.get("current").asJsonMap()
            ?: return@withContext WeatherState.Unavailable("Weather unavailable")
        val temp = current.jsonDouble("temperature_2m") ?: return@withContext WeatherState.Unavailable("Weather unavailable")
        val code = current.jsonInt("weather_code") ?: -1
        val isDay = (current.jsonInt("is_day") ?: 1) == 1
        val info = WeatherInfo(place.name, temp, code, isDay, System.currentTimeMillis(), sourceKey(s))
        last = info
        WeatherState.Ready(info, stale = false)
    }

    private fun resolvePlace(s: NovaSettings): Place? {
        if (s.weatherUseLocation) {
            lastKnownLocation()?.let { return Place("Current location", it.latitude, it.longitude) }
        }
        val city = s.weatherCity.trim()
        if (city.isEmpty()) return null
        val key = city.lowercase()
        geocodeCache[key]?.let { return it }
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", "en")
            .addQueryParameter("format", "json")
            .build()
        val first = getJson(url).asJsonMap()?.get("results").asJsonList()?.firstOrNull().asJsonMap() ?: return null
        val lat = first.jsonDouble("latitude") ?: return null
        val lon = first.jsonDouble("longitude") ?: return null
        val name = first.jsonString("name") ?: city
        return Place(name, lat, lon).also { geocodeCache[key] = it }
    }

    private fun getJson(url: HttpUrl): Any? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            return MiniJson.parseOrNull(r.peekBody(MAX_BODY).string())
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownLocation(): Location? {
        if (!Permissions.location(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)) {
            val candidate = try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            } ?: continue
            val current = best
            if (current == null || candidate.time > current.time) best = candidate
        }
        return best
    }

    private companion object {
        const val USER_AGENT = "NovaPill/1.0 (Android; app.novapill.android)"
        const val MAX_BODY = 512L * 1024L
        const val MIN_RETRY_MS = 30_000L
    }
}
