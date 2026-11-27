package app.novapill.android.util

/**
 * Tiny dependency-free JSON reader/writer.
 *
 * Why not org.json? It is an Android framework class, so JVM unit tests would need an extra
 * artifact. This keeps settings, network parsing and tests on exactly the same code path.
 *
 * Parsed values: Map<String, Any?> (insertion ordered), List<Any?>, String, Long, Double, Boolean, null.
 */
object MiniJson {
    private const val MAX_DEPTH = 48

    fun parseOrNull(text: String?): Any? {
        if (text.isNullOrBlank()) return null
        return try {
            parse(text)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue(0)
        reader.skipWhitespace()
        require(reader.atEnd()) { "Trailing data at ${reader.pos}" }
        return value
    }

    fun stringify(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value)
        return sb.toString()
    }

    private fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Float -> writeDouble(sb, value.toDouble())
            is Double -> writeDouble(sb, value)
            is Enum<*> -> writeString(sb, value.name)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            else -> writeString(sb, value.toString())
        }
    }

    private fun writeDouble(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) sb.append("null") else sb.append(d.toString())
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') {
                    sb.append("\\u")
                    sb.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(ch)
                }
            }
        }
        sb.append('"')
    }

    private class Reader(private val s: String) {
        var pos = 0

        fun atEnd(): Boolean = pos >= s.length

        fun skipWhitespace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun readValue(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "Nesting too deep" }
            skipWhitespace()
            require(!atEnd()) { "Unexpected end of input" }
            return when (val c = s[pos]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw IllegalArgumentException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            require(s.startsWith(word, pos)) { "Bad literal at $pos" }
            pos += word.length
            return value
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            val map = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "Expected string key at $pos" }
                val key = readString()
                skipWhitespace()
                require(peek() == ':') { "Expected ':' at $pos" }
                pos++
                map[key] = readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            val list = ArrayList<Any?>()
            pos++ // [
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (true) {
                list.add(readValue(depth + 1))
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
                }
            }
        }

        private fun peek(): Char {
            require(!atEnd()) { "Unexpected end of input" }
            return s[pos]
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                require(!atEnd()) { "Unterminated string" }
                val c = s[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        require(!atEnd()) { "Bad escape" }
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(pos + 4 <= s.length) { "Bad unicode escape" }
                                val hex = s.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: throw IllegalArgumentException("Bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readNumber(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            while (pos < s.length && s[pos] in '0'..'9') pos++
            var isDouble = false
            if (pos < s.length && s[pos] == '.') {
                isDouble = true
                pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos] in '0'..'9') pos++
            }
            val token = s.substring(start, pos)
            require(token.isNotEmpty() && token != "-") { "Bad number at $start" }
            return if (isDouble) {
                token.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number '$token'")
            } else {
                token.toLongOrNull() ?: token.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number '$token'")
            }
        }
    }
}

/** Safe typed accessors for parsed JSON values. */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonMap(): Map<String, Any?>? = this as? Map<String, Any?>

fun Any?.asJsonList(): List<Any?>? = this as? List<Any?>

fun Map<String, Any?>.jsonString(key: String): String? = this[key] as? String

fun Map<String, Any?>.jsonBool(key: String): Boolean? = this[key] as? Boolean

fun Map<String, Any?>.jsonLong(key: String): Long? = when (val v = this[key]) {
    is Long -> v
    is Int -> v.toLong()
    is Double -> if (v.isNaN() || v.isInfinite()) null else v.toLong()
    else -> null
}

fun Map<String, Any?>.jsonInt(key: String): Int? = jsonLong(key)?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())?.toInt()

fun Map<String, Any?>.jsonDouble(key: String): Double? = when (val v = this[key]) {
    is Double -> v
    is Long -> v.toDouble()
    is Int -> v.toDouble()
    else -> null
}
