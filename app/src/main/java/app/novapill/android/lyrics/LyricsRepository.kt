package app.novapill.android.lyrics

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import app.novapill.android.data.SettingsRepository
import app.novapill.android.settings.LyricsProviderChoice
import app.novapill.android.util.Diagnostics
import app.novapill.android.util.MiniJson
import app.novapill.android.util.asJsonList
import app.novapill.android.util.asJsonMap
import app.novapill.android.util.jsonBool
import app.novapill.android.util.jsonDouble
import app.novapill.android.util.jsonLong
import app.novapill.android.util.jsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class LyricsSource { IMPORTED, LRCLIB }

enum class LyricsUnavailable { PROVIDER_OFF, NOT_FOUND, NETWORK, ERROR, NO_TRACK }

enum class ImportResult { OK, EMPTY, TOO_LARGE, UNREADABLE, NO_TRACK }

sealed interface LyricsState {
    data object Idle : LyricsState
    data class Loading(val key: String) : LyricsState

    /**
     * [syncKind] NONE means the text has no timing at all (shown as "Unsynced lyrics"). LINE and WORD only
     * occur when the source file really carries those timestamps.
     */
    data class Ready(
        val key: String,
        val lines: List<LyricLine>,
        val syncKind: SyncKind,
        val source: LyricsSource,
        val instrumental: Boolean,
        val fromCache: Boolean,
    ) : LyricsState

    data class Unavailable(val key: String, val reason: LyricsUnavailable) : LyricsState

    /** Several plausible tracks: the user picks, the app never guesses. */
    data class Pick(val key: String, val options: List<TrackMatcher.Scored>) : LyricsState
}

/**
 * Finds timestamped lyrics for the track that is playing, in this order:
 *  1. a .LRC file the user imported for this track (always allowed, never leaves the device),
 *  2. only when the user switched the provider to LRCLIB: the local cache, then lrclib.net.
 * Nothing is ever written by the app itself. Requests go out only while the lyrics panel is open and
 * carry just title, artist, album and duration of the current track.
 */
class LyricsRepository(
    private val context: Context,
    private val settings: SettingsRepository,
    private val http: OkHttpClient,
    private val diagnostics: Diagnostics,
    private val scope: CoroutineScope,
) {
    private class Record(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Double,
        val instrumental: Boolean,
        val plain: String?,
        val synced: String?,
    )

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    private var job: Job? = null
    private var currentKey: String? = null
    private var currentQuery: TrackMatcher.Query? = null
    private val records = ConcurrentHashMap<String, Record>()
    private val missUntil = ConcurrentHashMap<String, Long>()
    private var lastNetworkAt = 0L

    private val localDir: File get() = File(context.filesDir, "lyrics_local")
    private val cacheDir: File get() = File(context.cacheDir, "lyrics_lrclib")

    /** Asks for lyrics of [query]. Does nothing when the same track is already loaded, unless [force]. */
    fun request(query: TrackMatcher.Query, force: Boolean = false) {
        if (query.title.isBlank()) {
            job?.cancel()
            currentKey = null
            currentQuery = null
            _state.value = LyricsState.Unavailable("", LyricsUnavailable.NO_TRACK)
            return
        }
        val key = TrackMatcher.trackKey(query.artist, query.title)
        if (!force && key == currentKey && _state.value !is LyricsState.Idle) return
        currentKey = key
        currentQuery = query
        job?.cancel()
        _state.value = LyricsState.Loading(key)
        job = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { resolve(query, key, force) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.error("lyrics", e)
                LyricsState.Unavailable(key, LyricsUnavailable.ERROR)
            }
            if (currentKey == key) _state.value = result
        }
    }

    fun retry() {
        val q = currentQuery ?: return
        request(q, force = true)
    }

    /** The panel closed or the island stopped: forget the current track so the next open starts clean. */
    fun release() {
        job?.cancel()
        currentKey = null
        currentQuery = null
        _state.value = LyricsState.Idle
    }

    fun choose(option: TrackMatcher.Scored) {
        val key = currentKey ?: return
        val q = currentQuery ?: return
        val rec = records[option.candidate.id] ?: return
        job?.cancel()
        _state.value = LyricsState.Loading(key)
        job = scope.launch {
            val result = withContext(Dispatchers.IO) { finish(q, key, rec) }
            if (currentKey == key) _state.value = result
        }
    }

    // ---- local files ----

    suspend fun importLrc(uri: Uri, title: String, artist: String): ImportResult = withContext(Dispatchers.IO) {
        importBlocking(uri, title, artist)
    }

    private fun importBlocking(uri: Uri, title: String, artist: String): ImportResult {
        if (title.isBlank()) return ImportResult.NO_TRACK
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { readLimited(it, MAX_IMPORT_BYTES) }
        } catch (_: Exception) {
            return ImportResult.UNREADABLE
        } ?: return ImportResult.TOO_LARGE
        val parsed = LrcParser.parse(String(bytes, Charsets.UTF_8))
        if (parsed.isEmpty) return ImportResult.EMPTY
        val key = TrackMatcher.trackKey(artist, title)
        return try {
            localDir.mkdirs()
            File(localDir, hash(key) + ".lrc").writeBytes(bytes)
            ImportResult.OK
        } catch (e: IOException) {
            diagnostics.error("lyrics", e)
            ImportResult.UNREADABLE
        }
    }

    fun removeImported(artist: String, title: String): Boolean =
        File(localDir, hash(TrackMatcher.trackKey(artist, title)) + ".lrc").delete()

    fun hasImported(artist: String, title: String): Boolean =
        title.isNotBlank() && File(localDir, hash(TrackMatcher.trackKey(artist, title)) + ".lrc").isFile

    fun importedCount(): Int = localDir.listFiles()?.size ?: 0

    fun cachedCount(): Int = cacheDir.listFiles()?.size ?: 0

    /** Deletes lyrics downloaded from LRCLIB. Imported files are only removed by [deleteImported]. */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        missUntil.clear()
        records.clear()
    }

    fun deleteImported() {
        localDir.listFiles()?.forEach { it.delete() }
    }

    fun debugSummary(): String = when (val s = _state.value) {
        LyricsState.Idle -> "idle"
        is LyricsState.Loading -> "loading"
        is LyricsState.Ready -> "ready, sync=${s.syncKind}, source=${s.source}${if (s.fromCache) ", cached" else ""}"
        is LyricsState.Unavailable -> "unavailable (${s.reason})"
        is LyricsState.Pick -> "needs choice (${s.options.size} candidates)"
    }

    // ---- resolution ----

    private suspend fun resolve(q: TrackMatcher.Query, key: String, force: Boolean): LyricsState {
        readLocal(key)?.let { return it }
        if (settings.settings.value.lyricsProvider != LyricsProviderChoice.LRCLIB) {
            return LyricsState.Unavailable(key, LyricsUnavailable.PROVIDER_OFF)
        }
        if (!force) {
            readCache(q, key)?.let { return it }
            val miss = missUntil[key]
            if (miss != null && SystemClock.elapsedRealtime() < miss) {
                return LyricsState.Unavailable(key, LyricsUnavailable.NOT_FOUND)
            }
        }
        return fetchOnline(q, key)
    }

    private fun readLocal(key: String): LyricsState? {
        val file = File(localDir, hash(key) + ".lrc")
        if (!file.isFile) return null
        val parsed = try {
            LrcParser.parse(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            return null
        }
        if (parsed.isEmpty) return null
        return LyricsState.Ready(key, parsed.lines, parsed.syncKind, LyricsSource.IMPORTED, instrumental = false, fromCache = false)
    }

    private fun readCache(q: TrackMatcher.Query, key: String): LyricsState? {
        val file = File(cacheDir, hash(TrackMatcher.cacheKey(q.artist, q.title, q.durationMs)) + ".json")
        if (!file.isFile) return null
        val map = MiniJson.parseOrNull(try { file.readText(Charsets.UTF_8) } catch (_: Exception) { return null }).asJsonMap()
            ?: return null
        return build(
            key,
            instrumental = map.jsonBool("instrumental") ?: false,
            synced = map.jsonString("synced"),
            plain = map.jsonString("plain"),
            fromCache = true,
        )
    }

    private fun writeCache(q: TrackMatcher.Query, rec: Record) {
        try {
            cacheDir.mkdirs()
            val body = LinkedHashMap<String, Any?>()
            body["id"] = rec.id
            body["instrumental"] = rec.instrumental
            body["synced"] = rec.synced
            body["plain"] = rec.plain
            File(cacheDir, hash(TrackMatcher.cacheKey(q.artist, q.title, q.durationMs)) + ".json")
                .writeText(MiniJson.stringify(body), Charsets.UTF_8)
        } catch (e: IOException) {
            diagnostics.error("lyrics", e)
        }
    }

    private fun build(key: String, instrumental: Boolean, synced: String?, plain: String?, fromCache: Boolean): LyricsState? {
        if (instrumental) {
            return LyricsState.Ready(key, emptyList(), SyncKind.NONE, LyricsSource.LRCLIB, instrumental = true, fromCache = fromCache)
        }
        if (!synced.isNullOrBlank()) {
            val parsed = LrcParser.parse(synced)
            if (!parsed.isEmpty) {
                return LyricsState.Ready(key, parsed.lines, parsed.syncKind, LyricsSource.LRCLIB, instrumental = false, fromCache = fromCache)
            }
        }
        if (!plain.isNullOrBlank()) {
            val lines = plain.lineSequence().map { it.trimEnd() }.map { LyricLine(0L, it) }.toList()
            return LyricsState.Ready(key, lines, SyncKind.NONE, LyricsSource.LRCLIB, instrumental = false, fromCache = fromCache)
        }
        return null
    }

    private fun finish(q: TrackMatcher.Query, key: String, rec: Record): LyricsState {
        val state = build(key, rec.instrumental, rec.synced, rec.plain, fromCache = false)
            ?: return LyricsState.Unavailable(key, LyricsUnavailable.NOT_FOUND)
        writeCache(q, rec)
        return state
    }

    private suspend fun fetchOnline(q: TrackMatcher.Query, key: String): LyricsState {
        try {
            if (q.album.isNotBlank() && q.durationMs > 0L) {
                throttle()
                val exact = fetchExact(q)
                if (exact != null && abs(exact.durationSec * 1000.0 - q.durationMs) <= EXACT_TOLERANCE_MS) {
                    val state = build(key, exact.instrumental, exact.synced, exact.plain, fromCache = false)
                    if (state != null) {
                        writeCache(q, exact)
                        return state
                    }
                }
            }
            throttle()
            val found = fetchSearch(q)
            records.clear()
            for (r in found) records[r.id.toString()] = r
            val selection = TrackMatcher.select(q, found.map { toCandidate(it) })
            return when (selection) {
                TrackMatcher.Selection.None -> {
                    missUntil[key] = SystemClock.elapsedRealtime() + MISS_TTL_MS
                    LyricsState.Unavailable(key, LyricsUnavailable.NOT_FOUND)
                }
                is TrackMatcher.Selection.Single -> {
                    val rec = records[selection.chosen.candidate.id]
                    if (rec == null) LyricsState.Unavailable(key, LyricsUnavailable.NOT_FOUND) else finish(q, key, rec)
                }
                is TrackMatcher.Selection.Ambiguous -> LyricsState.Pick(key, selection.options)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            diagnostics.error("lyrics", e)
            return LyricsState.Unavailable(key, LyricsUnavailable.NETWORK)
        } catch (e: Exception) {
            diagnostics.error("lyrics", e)
            return LyricsState.Unavailable(key, LyricsUnavailable.ERROR)
        }
    }

    /** At most one request per second, as a courtesy to a free community service. */
    private suspend fun throttle() {
        val wait = lastNetworkAt + MIN_GAP_MS - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        lastNetworkAt = SystemClock.elapsedRealtime()
    }

    private fun fetchExact(q: TrackMatcher.Query): Record? {
        val url = apiUrl("api/get")
            .addQueryParameter("track_name", q.title)
            .addQueryParameter("artist_name", q.artist)
            .addQueryParameter("album_name", q.album)
            .addQueryParameter("duration", (q.durationMs / 1000L).toString())
            .build()
        return parseRecord(call(url))
    }

    private fun fetchSearch(q: TrackMatcher.Query): List<Record> {
        val url = apiUrl("api/search")
            .addQueryParameter("track_name", q.title)
            .addQueryParameter("artist_name", q.artist)
            .build()
        return call(url).asJsonList()?.mapNotNull { parseRecord(it) }?.take(MAX_RESULTS) ?: emptyList()
    }

    private fun apiUrl(path: String): HttpUrl.Builder = BASE_URL.toHttpUrl().newBuilder().addPathSegments(path)

    /** Parsed JSON body, or null on 404. Anything else that is not 2xx is an IOException. */
    private fun call(url: HttpUrl): Any? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        http.newCall(request).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            return MiniJson.parseOrNull(r.peekBody(MAX_BODY).string())
        }
    }

    private fun parseRecord(v: Any?): Record? {
        val m = v.asJsonMap() ?: return null
        val id = m.jsonLong("id") ?: return null
        return Record(
            id = id,
            title = m.jsonString("trackName") ?: m.jsonString("name") ?: "",
            artist = m.jsonString("artistName") ?: "",
            album = m.jsonString("albumName") ?: "",
            durationSec = m.jsonDouble("duration") ?: 0.0,
            instrumental = m.jsonBool("instrumental") ?: false,
            plain = m.jsonString("plainLyrics")?.takeIf { it.isNotBlank() },
            synced = m.jsonString("syncedLyrics")?.takeIf { it.isNotBlank() },
        )
    }

    private fun toCandidate(r: Record): TrackMatcher.Candidate {
        val fingerprint = when {
            r.synced != null -> TrackMatcher.fingerprint(LrcParser.parse(r.synced).lines)
            r.plain != null -> TrackMatcher.strict(r.plain).takeIf { it.isNotEmpty() }
            else -> null
        }
        return TrackMatcher.Candidate(
            id = r.id.toString(),
            title = r.title,
            artist = r.artist,
            album = r.album,
            durationMs = (r.durationSec * 1000.0).toLong(),
            hasSynced = r.synced != null,
            hasPlain = r.plain != null,
            instrumental = r.instrumental,
            fingerprint = fingerprint,
        )
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
            .take(24)

    private companion object {
        const val BASE_URL = "https://lrclib.net/"
        const val USER_AGENT = "NovaPill/1.0 (Android; app.novapill.android)"
        const val MAX_BODY = 1_500_000L
        const val MAX_RESULTS = 20
        const val MAX_IMPORT_BYTES = 700_000
        const val MIN_GAP_MS = 1_000L
        const val MISS_TTL_MS = 15L * 60L * 1000L
        const val EXACT_TOLERANCE_MS = 3_000.0
    }
}
