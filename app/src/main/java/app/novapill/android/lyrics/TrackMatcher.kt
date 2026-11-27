package app.novapill.android.lyrics

import java.text.Normalizer
import kotlin.math.abs

/**
 * Track identity + lyric candidate matching. The rule is: validate, never guess.
 * Title and artist are compared after careful normalisation (feat/remaster noise is ignored, but
 * identity words such as "live", "remix", "acoustic" or "instrumental" are kept), and the duration
 * is used as a strong validator.
 */
object TrackMatcher {
    private const val STRONG_DURATION_MS = 2_000L
    private const val MAX_DURATION_DELTA_MS = 5_000L

    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val BRACKET_GROUP = Regex("[(\\[{]([^)\\]}]*)[)\\]}]")
    private val NOISE_CONTENT = Regex(
        "^(feat|ft|featuring|with|prod|remaster(ed)?|\\d{4} remaster(ed)?|remaster(ed)? \\d{4}|explicit|clean|deluxe|bonus track|album version|single version|mono|stereo)( .*)?$",
    )
    private val TRAILING_DASH_NOISE = Regex(
        "\\s[-\\u2013\\u2014]\\s(remaster(ed)?.*|\\d{4} remaster(ed)?.*|mono|stereo|single version|album version|radio edit)$",
        RegexOption.IGNORE_CASE,
    )
    private val FEAT_TAIL = Regex("\\s(feat\\.?|ft\\.?|featuring)\\s.+$", RegexOption.IGNORE_CASE)
    private val ARTIST_SPLIT = Regex(
        "\\s*(?:,|;|/|\\bfeat\\.?\\s|\\bft\\.?\\s|\\bfeaturing\\s|\\bwith\\s|\\sx\\s|\\s&\\s|\\sand\\s)\\s*",
        RegexOption.IGNORE_CASE,
    )

    /** Lower-case, accent-free, punctuation-free form. Identity words are kept. */
    fun strict(s: String): String {
        val folded = Normalizer.normalize(s, Normalizer.Form.NFKD).replace(DIACRITICS, "")
        return folded.lowercase().replace("&", " and ").replace(NON_ALNUM, " ").trim()
    }

    /** Like [strict] but also drops credit/remaster noise such as "(feat. X)" or "- 2011 Remaster". */
    fun loose(s: String): String {
        var t = s.replace(BRACKET_GROUP) { m -> if (NOISE_CONTENT.matches(strict(m.groupValues[1]))) " " else m.value }
        t = t.replace(TRAILING_DASH_NOISE, "")
        t = t.replace(FEAT_TAIL, "")
        return strict(t)
    }

    fun primaryArtist(artist: String): String {
        val first = artist.split(ARTIST_SPLIT).firstOrNull { it.isNotBlank() } ?: artist
        return strict(first)
    }

    /** Stable key for per-track offsets and imported .lrc files (duration independent). */
    fun trackKey(artist: String, title: String): String = strict(artist) + "|" + strict(title)

    /** Key for provider results (duration bucketed to whole seconds). */
    fun cacheKey(artist: String, title: String, durationMs: Long): String =
        trackKey(artist, title) + "|" + (if (durationMs > 0) durationMs / 1000 else 0)

    data class Query(val title: String, val artist: String, val album: String, val durationMs: Long)

    data class Candidate(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val hasSynced: Boolean,
        val hasPlain: Boolean,
        val instrumental: Boolean,
        /** Normalised lyric text without timestamps, used to recognise duplicate entries. */
        val fingerprint: String? = null,
    ) {
        val usable: Boolean get() = hasSynced || hasPlain || instrumental
    }

    data class Scored(
        val candidate: Candidate,
        val titleScore: Double,
        val artistScore: Double,
        val durationDeltaMs: Long?,
        val strong: Boolean,
    ) {
        val total: Double get() = titleScore * 0.6 + artistScore * 0.4
    }

    sealed interface Selection {
        data object None : Selection
        data class Single(val chosen: Scored) : Selection
        data class Ambiguous(val options: List<Scored>) : Selection
    }

    fun titleScore(query: String, candidate: String): Double {
        val qs = strict(query)
        val cs = strict(candidate)
        if (qs.isEmpty() || cs.isEmpty()) return 0.0
        if (qs == cs) return 1.0
        val ql = loose(query)
        val cl = loose(candidate)
        if (ql.isNotEmpty() && ql == cl) return 0.9
        val j = tokenOverlap(ql, cl)
        return if (j >= 0.8) j * 0.8 else 0.0
    }

    fun artistScore(query: String, candidate: String): Double {
        val qs = strict(query)
        val cs = strict(candidate)
        if (qs.isEmpty() || cs.isEmpty()) return 0.5
        if (qs == cs) return 1.0
        val qp = primaryArtist(query)
        val cp = primaryArtist(candidate)
        if (qp.isNotEmpty() && qp == cp) return 0.85
        val qt = qs.split(' ').filter { it.isNotEmpty() }.toSet()
        val ct = cs.split(' ').filter { it.isNotEmpty() }.toSet()
        if (qt.isNotEmpty() && ct.isNotEmpty() && (qt.containsAll(ct) || ct.containsAll(qt))) return 0.7
        return 0.0
    }

    private fun tokenOverlap(a: String, b: String): Double {
        val ta = a.split(' ').filter { it.isNotEmpty() }.toSet()
        val tb = b.split(' ').filter { it.isNotEmpty() }.toSet()
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.count { it in tb }
        val union = ta.size + tb.size - inter
        return inter.toDouble() / union.toDouble()
    }

    fun score(query: Query, c: Candidate): Scored? {
        if (!c.usable) return null
        val title = titleScore(query.title, c.title)
        val artist = artistScore(query.artist, c.artist)
        if (title < 0.6 || artist < 0.5) return null
        val delta = if (query.durationMs > 0 && c.durationMs > 0) abs(query.durationMs - c.durationMs) else null
        if (delta != null && delta > MAX_DURATION_DELTA_MS) return null
        val durationStrong = if (delta != null) delta <= STRONG_DURATION_MS else false
        val identityStrong = title >= 0.9 && artist >= (if (query.durationMs > 0) 0.8 else 0.9)
        return Scored(c, title, artist, delta, identityStrong && durationStrong)
    }

    /**
     * Picks one candidate only when it is unambiguous. Several strong, different candidates (or only weak
     * ones) produce [Selection.Ambiguous] so the user chooses instead of the app guessing.
     */
    fun select(query: Query, candidates: List<Candidate>): Selection {
        val scored = candidates.mapNotNull { score(query, it) }
        if (scored.isEmpty()) return Selection.None

        val ordered = scored.sortedWith(
            compareByDescending<Scored> { it.strong }
                .thenByDescending { it.total }
                .thenBy { it.durationDeltaMs ?: Long.MAX_VALUE },
        )
        val strong = ordered.filter { it.strong }
        if (strong.isEmpty()) return Selection.Ambiguous(ordered.take(5))

        val preferred = strong.filter { it.candidate.hasSynced }.ifEmpty { strong }
        if (preferred.size == 1) return Selection.Single(preferred.first())

        val prints = preferred.map { it.candidate.fingerprint }
        val allSame = prints.all { it != null } && prints.distinct().size == 1
        return if (allSame) {
            Selection.Single(preferred.minByOrNull { it.durationDeltaMs ?: Long.MAX_VALUE } ?: preferred.first())
        } else {
            Selection.Ambiguous(preferred.take(5))
        }
    }

    /** Fingerprint of lyric text ignoring timestamps and whitespace differences. */
    fun fingerprint(lines: List<LyricLine>): String? {
        val text = lines.joinToString(" ") { it.text }.let { strict(it) }
        return text.takeIf { it.isNotEmpty() }
    }
}
