package app.novapill.android.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import app.novapill.android.core.MediaPlayState
import app.novapill.android.data.SettingsRepository
import app.novapill.android.lyrics.TrackMatcher
import app.novapill.android.service.ListenerBridge
import app.novapill.android.util.AppInfoCache
import app.novapill.android.util.Diagnostics
import app.novapill.android.util.IntentLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Everything the island shows about the selected media session. Nothing here is ever invented. */
data class MediaUi(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artwork: Bitmap?,
    val accentArgb: Int?,
    val playback: PlaybackInfo,
) {
    val state: MediaPlayState get() = playback.state
    val hasTitle: Boolean get() = title.isNotBlank()

    /** Changes when the player or the track changes; used to key animations and lyric lookups. */
    val trackId: String get() = "$packageName|$title|$artist|$durationMs"

    val lyricsKey: String? get() = if (hasTitle) TrackMatcher.trackKey(artist, title) else null

    fun positionNow(): Long = playback.positionAt(SystemClock.elapsedRealtime(), durationMs)
}

/**
 * Follows Android media sessions through MediaSessionManager (needs the notification listener to be
 * connected). One session is "selected" for display; every transport command goes to that same
 * session's controller and is refused when the screen shows a different player.
 * All methods run on the main thread.
 */
class MediaMonitor(
    private val context: Context,
    private val settings: SettingsRepository,
    private val appInfo: AppInfoCache,
    private val http: OkHttpClient,
    private val diagnostics: Diagnostics,
) {
    private class Tracked(val controller: MediaController) {
        lateinit var callback: MediaController.Callback
        var title = ""
        var artist = ""
        var album = ""
        var duration = 0L
        var artwork: Bitmap? = null
        var artworkUri: String? = null
        var accent: Int? = null
        var playback: PlaybackInfo = PlaybackInfo.NONE
        var lastPlayingAt = 0L

        fun applyPlayback(s: PlaybackState?) {
            if (s == null) {
                playback = PlaybackInfo.NONE
                return
            }
            val mapped = PlaybackStateMapper.fromCode(s.state)
            val updated = s.lastPositionUpdateTime
            val base = if (updated > 0L) updated else SystemClock.elapsedRealtime()
            playback = PlaybackInfo(mapped, s.position.coerceAtLeast(0L), s.playbackSpeed, base, s.actions)
            if (mapped == MediaPlayState.PLAYING) lastPlayingAt = SystemClock.elapsedRealtime()
        }
    }

    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val component: ComponentName = ListenerBridge.component(context)
    private val handler = Handler(Looper.getMainLooper())
    private val tracked = LinkedHashMap<MediaSession.Token, Tracked>()
    private val artCache = LruCache<String, Bitmap>(6)

    private var scope: CoroutineScope? = null
    private var prefJob: Job? = null
    private var started = false
    private var selectedToken: MediaSession.Token? = null

    private val _current = MutableStateFlow<MediaUi?>(null)
    val current: StateFlow<MediaUi?> = _current.asStateFlow()

    private val _linked = MutableStateFlow(false)

    /** True while a session list could be read, which needs the notification listener. */
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    private val _players = MutableStateFlow<List<String>>(emptyList())

    /** Package names of every active media session (for the player picker and diagnostics). */
    val players: StateFlow<List<String>> = _players.asStateFlow()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        onSessions(list?.toList() ?: emptyList())
    }

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        this.scope = scope
        prefJob = scope.launch {
            settings.settings.map { it.preferredPlayer }.distinctUntilChanged().collect { publish() }
        }
        if (ListenerBridge.connected.value) registerSessions()
    }

    fun stop() {
        started = false
        prefJob?.cancel()
        prefJob = null
        unregisterAll()
        _current.value = null
        scope = null
    }

    fun onListenerConnected() {
        if (started) registerSessions()
    }

    fun onListenerDisconnected() {
        unregisterAll()
        publish()
    }

    /** Re-reads the session list, for example when the app comes back to the foreground. */
    fun refresh() {
        if (started && ListenerBridge.connected.value) registerSessions()
    }

    private fun registerSessions() {
        val m = manager ?: return
        try {
            m.removeOnActiveSessionsChangedListener(sessionsListener)
            m.addOnActiveSessionsChangedListener(sessionsListener, component)
            _linked.value = true
            onSessions(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            _linked.value = false
            diagnostics.error("media", e)
        } catch (e: Exception) {
            diagnostics.error("media", e)
        }
    }

    private fun unregisterAll() {
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
            // nothing registered
        }
        for (tr in tracked.values) {
            try {
                tr.controller.unregisterCallback(tr.callback)
            } catch (_: Exception) {
                // session already gone
            }
        }
        tracked.clear()
        selectedToken = null
        _linked.value = false
        _players.value = emptyList()
    }

    private fun onSessions(controllers: List<MediaController>) {
        val live = HashSet<MediaSession.Token>()
        for (c in controllers) {
            val token = c.sessionToken
            live.add(token)
            if (!tracked.containsKey(token)) track(c)
        }
        for (token in tracked.keys.filter { it !in live }) untrack(token)
        publish()
    }

    private fun track(c: MediaController) {
        val tr = Tracked(c)
        val token = c.sessionToken
        val cb = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                tr.applyPlayback(state)
                publish()
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                applyMetadata(tr, metadata)
                publish()
            }

            override fun onSessionDestroyed() {
                untrack(token)
                publish()
            }
        }
        tr.callback = cb
        try {
            c.registerCallback(cb, handler)
        } catch (e: Exception) {
            diagnostics.error("media", e)
            return
        }
        tr.applyPlayback(c.playbackState)
        applyMetadata(tr, c.metadata)
        tracked[token] = tr
    }

    private fun untrack(token: MediaSession.Token) {
        val tr = tracked.remove(token) ?: return
        try {
            tr.controller.unregisterCallback(tr.callback)
        } catch (_: Exception) {
            // already unregistered
        }
    }

    private fun applyMetadata(tr: Tracked, m: MediaMetadata?) {
        if (m == null) {
            tr.title = ""
            tr.artist = ""
            tr.album = ""
            tr.duration = 0L
            tr.artwork = null
            tr.artworkUri = null
            tr.accent = null
            return
        }
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_AUTHOR)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val newTrack = title != tr.title || artist != tr.artist || duration != tr.duration

        tr.title = title
        tr.artist = artist
        tr.album = album
        tr.duration = duration

        val bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        when {
            bitmap != null -> {
                val scaled = scaleDown(bitmap, MAX_ART_PX)
                tr.artwork = scaled
                tr.artworkUri = null
                tr.accent = accentOf(scaled)
            }
            uri != null -> {
                if (uri != tr.artworkUri || newTrack) {
                    val cached = artCache.get(uri)
                    tr.artworkUri = uri
                    tr.artwork = cached
                    tr.accent = accentOf(cached)
                    if (cached == null) loadArtwork(tr, uri)
                }
            }
            else -> {
                tr.artwork = null
                tr.artworkUri = null
                tr.accent = null
            }
        }
    }

    private fun loadArtwork(tr: Tracked, uri: String) {
        val sc = scope ?: return
        sc.launch {
            val bmp = withContext(Dispatchers.IO) { fetchArtwork(uri) }
            if (bmp != null && tr.artworkUri == uri && tracked.containsValue(tr)) {
                artCache.put(uri, bmp)
                tr.artwork = bmp
                tr.accent = accentOf(bmp)
                publish()
            }
        }
    }

    private fun publish() {
        val entries = tracked.entries.toList()
        val candidates = entries.map { (_, t) ->
            SessionCandidate(t.controller.packageName, t.playback.state, t.title.isNotBlank(), t.lastPlayingAt)
        }
        _players.value = entries.map { it.value.controller.packageName }.distinct()
        val index = MediaSelector.select(candidates, settings.settings.value.preferredPlayer)
        if (index < 0) {
            selectedToken = null
            _current.value = null
            return
        }
        val (token, t) = entries[index]
        selectedToken = token
        val pkg = t.controller.packageName
        _current.value = MediaUi(
            packageName = pkg,
            appLabel = appInfo.label(pkg),
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationMs = t.duration,
            artwork = t.artwork,
            accentArgb = t.accent,
            playback = t.playback,
        )
    }

    // ---- transport: always the displayed session, only when it advertises the action ----

    private fun selected(): Tracked? {
        val token = selectedToken ?: return null
        val tr = tracked[token] ?: return null
        val shown = _current.value ?: return null
        return if (tr.controller.packageName == shown.packageName) tr else null
    }

    private inline fun send(block: (MediaController.TransportControls) -> Unit): Boolean {
        val tr = selected() ?: return false
        return try {
            block(tr.controller.transportControls)
            true
        } catch (e: Exception) {
            diagnostics.error("media", e)
            false
        }
    }

    fun canPlayPause(): Boolean {
        val a = selected()?.playback ?: return false
        return TransportActions.canPlay(a.actions) || TransportActions.canPause(a.actions)
    }

    fun togglePlayPause(): Boolean {
        val tr = selected() ?: return false
        val actions = tr.playback.actions
        return when (tr.playback.state) {
            MediaPlayState.PLAYING, MediaPlayState.BUFFERING ->
                TransportActions.canPause(actions) && send { it.pause() }
            else -> TransportActions.canPlay(actions) && send { it.play() }
        }
    }

    fun next(): Boolean {
        val actions = selected()?.playback?.actions ?: return false
        return TransportActions.canNext(actions) && send { it.skipToNext() }
    }

    fun previous(): Boolean {
        val actions = selected()?.playback?.actions ?: return false
        return TransportActions.canPrevious(actions) && send { it.skipToPrevious() }
    }

    fun seekTo(positionMs: Long): Boolean {
        val actions = selected()?.playback?.actions ?: return false
        return TransportActions.canSeek(actions) && send { it.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    /** Opens the player that owns the displayed session (its own session activity first). */
    fun openPlayer(): Boolean {
        val tr = selected() ?: return false
        if (IntentLauncher.send(context, tr.controller.sessionActivity)) return true
        val launch = appInfo.launchIntent(tr.controller.packageName) ?: return false
        return IntentLauncher.start(context, launch)
    }

    fun debugSummary(): String =
        "sessions=${tracked.size}, linked=${_linked.value}, shown=${_current.value?.packageName ?: "none"}"

    // ---- artwork helpers ----

    private fun scaleDown(bmp: Bitmap, max: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= max) return bmp
        val ratio = max.toFloat() / longest.toFloat()
        val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
        val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bmp, w, h, true)
        } catch (_: Exception) {
            bmp
        }
    }

    /** Average colour weighted towards vivid, bright pixels; null when the artwork is basically grey. */
    private fun accentOf(bmp: Bitmap?): Int? {
        if (bmp == null) return null
        return try {
            val small = Bitmap.createScaledBitmap(bmp, 8, 8, true)
            val hsv = FloatArray(3)
            var r = 0f
            var g = 0f
            var b = 0f
            var total = 0f
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val px = small.getPixel(x, y)
                    Color.colorToHSV(px, hsv)
                    val weight = hsv[1] * hsv[2]
                    r += Color.red(px) * weight
                    g += Color.green(px) * weight
                    b += Color.blue(px) * weight
                    total += weight
                }
            }
            if (small !== bmp) small.recycle()
            if (total < 0.5f) {
                null
            } else {
                Color.colorToHSV(Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt()), hsv)
                hsv[1] = minOf(1f, hsv[1] * 1.1f)
                hsv[2] = maxOf(hsv[2], 0.6f)
                Color.HSVToColor(hsv)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchArtwork(uriString: String): Bitmap? = try {
        val uri = Uri.parse(uriString)
        val bytes: ByteArray? = when (uri.scheme?.lowercase()) {
            "https" -> http.newCall(Request.Builder().url(uriString).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.byteStream()?.let { readLimited(it) } else null
            }
            "content" -> context.contentResolver.openInputStream(uri)?.use { readLimited(it) }
            else -> null
        }
        bytes?.let { decodeSampled(it) }
    } catch (_: Exception) {
        null
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_ART_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun decodeSampled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_ART_PX && bounds.outHeight / (sample * 2) >= MAX_ART_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private companion object {
        const val MAX_ART_PX = 320
        const val MAX_ART_BYTES = 2_000_000
    }
}
