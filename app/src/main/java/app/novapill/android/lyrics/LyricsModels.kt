package app.novapill.android.lyrics

/** One word with a real timestamp (only produced when the source file carries word tags). */
data class LyricWord(val startMs: Long, val text: String)

/**
 * One lyric line. [words] is non-null only when the source provided real per-word timestamps
 * (enhanced LRC `<mm:ss.xx>` tags). Line-level files never get invented word timing.
 */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord>? = null)

enum class SyncKind { NONE, LINE, WORD }

data class ParsedLrc(
    val lines: List<LyricLine>,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** Offset from an `[offset:]` tag that was already applied to [lines]. */
    val appliedOffsetMs: Long,
    val syncKind: SyncKind,
    val skippedLines: Int,
) {
    val isEmpty: Boolean get() = lines.none { it.text.isNotBlank() }
}
