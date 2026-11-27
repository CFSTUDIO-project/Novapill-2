package app.novapill.android.lyrics

import app.novapill.android.settings.NovaSettings

/** Pure lookup helpers. The caller supplies an already interpolated playback position. */
object LyricsSync {
    /** Cap for "how long a line lasts" when the next line is far away (instrumental breaks). */
    private const val MAX_LINE_SPAN_MS = 8_000L

    /** Position the lookup should use: positive offset = lyrics appear earlier. */
    fun lookupPosition(playbackMs: Long, offsetMs: Int): Long = playbackMs + offsetMs

    /**
     * Index of the last line whose timestamp is <= [positionMs], or -1 before the first line.
     * Lines sharing a timestamp resolve to the last of the group, so repeated position events never flap.
     */
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var answer = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                answer = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return answer
    }

    /** Active word inside a line that carries real word timing, else -1. */
    fun activeWordIndex(line: LyricLine, positionMs: Long): Int {
        val words = line.words ?: return -1
        var answer = -1
        for (i in words.indices) {
            if (words[i].startMs <= positionMs) answer = i else break
        }
        return answer
    }

    /** 0..1 progress through the current line, bounded by the next line (or a sane cap). */
    fun lineProgress(lines: List<LyricLine>, index: Int, positionMs: Long): Float {
        if (index !in lines.indices) return 0f
        val start = lines[index].timeMs
        val end = if (index + 1 < lines.size) lines[index + 1].timeMs else start + MAX_LINE_SPAN_MS
        val span = (minOf(end - start, MAX_LINE_SPAN_MS)).coerceAtLeast(1L)
        return ((positionMs - start).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    fun clampOffset(ms: Int): Int = ms.coerceIn(NovaSettings.OFFSET_MIN_MS, NovaSettings.OFFSET_MAX_MS)
}
