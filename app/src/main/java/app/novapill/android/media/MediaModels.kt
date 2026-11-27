package app.novapill.android.media

import app.novapill.android.core.MediaPlayState

/** Numeric values of android.media.session.PlaybackState.ACTION_* (stable public API constants). */
object TransportActions {
    const val STOP = 1L
    const val PAUSE = 2L
    const val PLAY = 4L
    const val REWIND = 8L
    const val SKIP_TO_PREVIOUS = 16L
    const val SKIP_TO_NEXT = 32L
    const val FAST_FORWARD = 64L
    const val SET_RATING = 128L
    const val SEEK_TO = 256L
    const val PLAY_PAUSE = 512L

    fun canPlay(actions: Long): Boolean = (actions and (PLAY or PLAY_PAUSE)) != 0L
    fun canPause(actions: Long): Boolean = (actions and (PAUSE or PLAY_PAUSE)) != 0L
    fun canNext(actions: Long): Boolean = (actions and SKIP_TO_NEXT) != 0L
    fun canPrevious(actions: Long): Boolean = (actions and SKIP_TO_PREVIOUS) != 0L
    fun canSeek(actions: Long): Boolean = (actions and SEEK_TO) != 0L
}

/** Maps android.media.session.PlaybackState.STATE_* codes to the states the island understands. */
object PlaybackStateMapper {
    fun fromCode(code: Int): MediaPlayState = when (code) {
        3, 4, 5 -> MediaPlayState.PLAYING // playing, fast forwarding, rewinding
        6, 8, 9, 10, 11 -> MediaPlayState.BUFFERING // buffering, connecting, skipping
        2 -> MediaPlayState.PAUSED
        1, 7 -> MediaPlayState.STOPPED // stopped, error
        else -> MediaPlayState.NONE
    }
}

/**
 * Snapshot of a playback state. The live position is interpolated from the last reported position,
 * the playback speed and a monotonic clock (SystemClock.elapsedRealtime), so no polling is needed.
 */
data class PlaybackInfo(
    val state: MediaPlayState,
    val basePositionMs: Long,
    val speed: Float,
    /** Monotonic instant at which [basePositionMs] was valid. */
    val baseElapsedMs: Long,
    val actions: Long,
) {
    fun positionAt(nowElapsedMs: Long, durationMs: Long): Long {
        val advancing = state == MediaPlayState.PLAYING && speed > 0f
        val raw = if (advancing) {
            basePositionMs + ((nowElapsedMs - baseElapsedMs).coerceAtLeast(0) * speed).toLong()
        } else {
            basePositionMs
        }
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return raw.coerceIn(0L, max)
    }

    companion object {
        val NONE = PlaybackInfo(MediaPlayState.NONE, 0, 1f, 0, 0)
    }
}

data class SessionCandidate(
    val packageName: String,
    val state: MediaPlayState,
    val hasMetadata: Boolean,
    /** Larger means played more recently (monotonic ms). */
    val recency: Long,
)

/** Chooses which media session the island shows and controls. It never guesses between players. */
object MediaSelector {
    val PRIORITY_PACKAGES = listOf("com.spotify.music", "com.google.android.apps.youtube.music")

    /** Index into [candidates] or -1. A non-blank [preferredPackage] is strict: other players are ignored. */
    fun select(candidates: List<SessionCandidate>, preferredPackage: String): Int {
        val ranked = candidates.withIndex().filter { (_, c) ->
            (preferredPackage.isBlank() || c.packageName == preferredPackage) &&
                !(c.state == MediaPlayState.NONE && !c.hasMetadata)
        }
        val best = ranked.maxWithOrNull(
            compareBy<IndexedValue<SessionCandidate>>(
                { statePriority(it.value.state) },
                { packagePriority(it.value.packageName) },
                { it.value.recency },
                { -it.index },
            ),
        )
        return best?.index ?: -1
    }

    private fun statePriority(s: MediaPlayState): Int = when (s) {
        MediaPlayState.PLAYING -> 5
        MediaPlayState.BUFFERING -> 4
        MediaPlayState.PAUSED -> 3
        MediaPlayState.STOPPED -> 2
        MediaPlayState.NONE -> 1
    }

    private fun packagePriority(pkg: String): Int {
        val i = PRIORITY_PACKAGES.indexOf(pkg)
        return if (i < 0) 0 else PRIORITY_PACKAGES.size - i
    }
}
