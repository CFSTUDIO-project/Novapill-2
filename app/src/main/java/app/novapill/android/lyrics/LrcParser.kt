package app.novapill.android.lyrics

/**
 * Parser for LRC and enhanced (word tagged) LRC.
 *
 * Supports `[mm:ss.xx]`, `[mm:ss.xxx]`, `[m:ss]`, several timestamps on one line, the metadata tags
 * `ti/ar/al/by/length/offset`, and `<mm:ss.xx>` word tags. Anything malformed is skipped and counted,
 * never guessed. The `[offset:]` tag follows the common convention: a positive value makes lyrics
 * appear earlier, so the effective time is `timestamp - offset`.
 */
object LrcParser {
    const val MAX_CHARS = 600_000
    const val MAX_LINES = 5_000

    private val TIME_TAG = Regex("""^(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?$""")
    private val META_TAG = Regex("""^([A-Za-z#]{1,12}):(.*)$""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

    fun parse(raw: String): ParsedLrc {
        var text = raw.removePrefix("﻿")
        if (text.length > MAX_CHARS) text = text.substring(0, MAX_CHARS)

        val meta = HashMap<String, String>()
        val parsed = ArrayList<LyricLine>()
        var skipped = 0

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            var pos = 0
            val stamps = ArrayList<Long>()
            var metaSeen = false
            var malformed = false

            while (pos < line.length && line[pos] == '[') {
                val close = line.indexOf(']', pos)
                if (close < 0) break
                val inner = line.substring(pos + 1, close)
                val time = TIME_TAG.matchEntire(inner)
                if (time != null) {
                    val ms = toMs(time.groupValues[1], time.groupValues[2], time.groupValues[3])
                    if (ms == null) {
                        malformed = true
                        break
                    }
                    stamps.add(ms)
                    pos = close + 1
                    continue
                }
                val tag = META_TAG.matchEntire(inner)
                if (tag != null && stamps.isEmpty()) {
                    meta[tag.groupValues[1].lowercase()] = tag.groupValues[2].trim()
                    metaSeen = true
                    pos = close + 1
                    continue
                }
                break
            }

            if (malformed) {
                skipped++
                continue
            }

            val body = line.substring(pos)
            if (stamps.isEmpty()) {
                if (!(metaSeen && body.isBlank())) skipped++
                continue
            }

            val (clean, words) = parseWords(body, stamps.first())
            for (stamp in stamps) {
                if (parsed.size >= MAX_LINES) break
                parsed.add(LyricLine(stamp, clean, if (stamps.size == 1) words else null))
            }
        }

        val offset = meta["offset"]?.trim()?.removePrefix("+")?.toLongOrNull() ?: 0L
        val shifted = if (offset == 0L) parsed else parsed.map { shift(it, -offset) }
        val sorted = shifted.sortedBy { it.timeMs }

        val nonBlank = sorted.count { it.text.isNotBlank() }
        val withWords = sorted.count { it.words != null && it.text.isNotBlank() }
        val kind = when {
            sorted.isEmpty() -> SyncKind.NONE
            nonBlank > 0 && withWords * 10 >= nonBlank * 9 -> SyncKind.WORD
            else -> SyncKind.LINE
        }

        return ParsedLrc(
            lines = sorted,
            title = meta["ti"]?.takeIf { it.isNotBlank() },
            artist = meta["ar"]?.takeIf { it.isNotBlank() },
            album = meta["al"]?.takeIf { it.isNotBlank() },
            appliedOffsetMs = offset,
            syncKind = kind,
            skippedLines = skipped,
        )
    }

    private fun shift(line: LyricLine, deltaMs: Long): LyricLine = LyricLine(
        timeMs = (line.timeMs + deltaMs).coerceAtLeast(0),
        text = line.text,
        words = line.words?.map { LyricWord((it.startMs + deltaMs).coerceAtLeast(0), it.text) },
    )

    private fun toMs(min: String, sec: String, frac: String): Long? {
        val m = min.toIntOrNull() ?: return null
        val s = sec.toIntOrNull() ?: return null
        if (s !in 0..59) return null
        val f = if (frac.isEmpty()) 0 else frac.padEnd(3, '0').take(3).toInt()
        return m * 60_000L + s * 1_000L + f
    }

    /** Returns the display text (tags removed) and real word timing if every tag is valid and ordered. */
    private fun parseWords(body: String, lineStart: Long): Pair<String, List<LyricWord>?> {
        val matches = WORD_TAG.findAll(body).toList()
        if (matches.isEmpty()) return body.trim() to null

        val words = ArrayList<LyricWord>()
        val parts = ArrayList<String>()
        var valid = true
        var last = Long.MIN_VALUE

        val lead = body.substring(0, matches.first().range.first).trim()
        if (lead.isNotEmpty()) {
            words.add(LyricWord(lineStart, lead))
            parts.add(lead)
            last = lineStart
        }
        for ((i, m) in matches.withIndex()) {
            val start = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else body.length
            val segment = body.substring(m.range.last + 1, end).trim()
            if (start == null) {
                valid = false
                if (segment.isNotEmpty()) parts.add(segment)
                continue
            }
            if (segment.isEmpty()) continue
            if (start < last) valid = false
            last = start
            words.add(LyricWord(start, segment))
            parts.add(segment)
        }
        val clean = parts.joinToString(" ")
        return clean to (if (valid && words.isNotEmpty()) words else null)
    }
}
