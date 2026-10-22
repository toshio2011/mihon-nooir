package dev.nooir.desktop.suwayomi

/**
 * Small bounded JSON reader for Suwayomi's GraphQL replies.
 * Deliberately dependency-free for the first JVM spike. Not a general JSON library.
 */
internal object MiniJson {
    fun parse(source: String): Any? {
        require(source.length <= 4_000_000) { "Suwayomi response exceeds JSON size limit" }
        val reader = Reader(source)
        val result = reader.value(0)
        reader.space()
        require(reader.atEnd()) { "Trailing JSON data" }
        return result
    }

    fun quote(value: String): String = buildString {
        append('"')
        for (c in value) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 32) append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private class Reader(val source: String) {
        var pos = 0
        fun atEnd() = pos == source.length
        fun space() { while (pos < source.length && source[pos].isWhitespace()) pos++ }
        fun next(): Char = source.getOrNull(pos++) ?: error("Truncated JSON")
        fun expect(c: Char) { require(next() == c) { "Expected JSON '$c'" } }
        fun value(depth: Int): Any? {
            require(depth <= 48) { "JSON nesting too deep" }
            space()
            return when (source.getOrNull(pos)) {
                '{' -> obj(depth + 1)
                '[' -> arr(depth + 1)
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                '-', in '0'..'9' -> number()
                else -> error("Invalid JSON at offset $pos")
            }
        }
        private fun <T> literal(token: String, result: T): T {
            require(source.startsWith(token, pos)) { "Invalid JSON literal" }
            pos += token.length
            return result
        }
        private fun obj(depth: Int): Map<String, Any?> {
            expect('{'); space()
            val out = linkedMapOf<String, Any?>()
            if (source.getOrNull(pos) == '}') { pos++; return out }
            while (true) {
                space()
                require(source.getOrNull(pos) == '"') { "JSON object key must be a string" }
                val key = string()
                space(); expect(':')
                val v = value(depth)
                require(!out.containsKey(key)) { "Duplicate JSON key" }
                out[key] = v
                space()
                when (next()) {
                    '}' -> return out
                    ',' -> continue
                    else -> error("Invalid JSON object separator")
                }
            }
        }
        private fun arr(depth: Int): List<Any?> {
            expect('['); space()
            val out = arrayListOf<Any?>()
            if (source.getOrNull(pos) == ']') { pos++; return out }
            while (true) {
                require(out.size < 100_000) { "JSON array too large" }
                out += value(depth)
                space()
                when (next()) {
                    ']' -> return out
                    ',' -> continue
                    else -> error("Invalid JSON array separator")
                }
            }
        }
        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(out.length <= 2_000_000) { "JSON string too large" }
                val c = next()
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        when (val escaped = next()) {
                            '"', '\\', '/' -> out.append(escaped)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(pos + 4 <= source.length) { "Truncated Unicode escape" }
                                val hex = source.substring(pos, pos + 4)
                                out.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid JSON escape")
                        }
                    }
                    else -> {
                        require(c.code >= 32) { "Unescaped control character in JSON" }
                        out.append(c)
                    }
                }
            }
        }
        private fun number(): Number {
            val start = pos
            if (source.getOrNull(pos) == '-') pos++
            if (source.getOrNull(pos) == '0') pos++ else {
                require(source.getOrNull(pos) in '1'..'9') { "Invalid JSON number" }
                while (source.getOrNull(pos) in '0'..'9') pos++
            }
            var fractional = false
            if (source.getOrNull(pos) == '.') {
                fractional = true; pos++
                require(source.getOrNull(pos) in '0'..'9') { "Invalid decimal" }
                while (source.getOrNull(pos) in '0'..'9') pos++
            }
            if (source.getOrNull(pos) == 'e' || source.getOrNull(pos) == 'E') {
                fractional = true; pos++
                if (source.getOrNull(pos) == '+' || source.getOrNull(pos) == '-') pos++
                require(source.getOrNull(pos) in '0'..'9') { "Invalid exponent" }
                while (source.getOrNull(pos) in '0'..'9') pos++
            }
            val token = source.substring(start, pos)
            return if (fractional) token.toDouble() else token.toLong()
        }
    }
}

internal fun Any?.asObject(): Map<String, Any?> {
    require(this is Map<*, *>) { "Expected JSON object" }
    @Suppress("UNCHECKED_CAST")
    return this as Map<String, Any?>
}
internal fun Any?.asList(): List<Any?> {
    require(this is List<*>) { "Expected JSON array" }
    return this
}
internal fun Map<String, Any?>.obj(key: String): Map<String, Any?> = this[key].asObject()
internal fun Map<String, Any?>.list(key: String): List<Any?> = this[key].asList()
internal fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: error("Missing $key")
internal fun Map<String, Any?>.int(key: String): Int {
    val number = this[key] as? Number ?: error("Missing $key")
    val value = number.toLong()
    require(value in Int.MIN_VALUE..Int.MAX_VALUE && number.toDouble() == value.toDouble()) { "Invalid $key" }
    return value.toInt()
}
