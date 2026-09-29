package org.lain.engine.script

class ScriptValueParseException(
    message: String,
    val offset: Int,
) : IllegalArgumentException("$message at offset $offset")

fun String.parseScriptValue(): ScriptValue = ScriptValueTextParser(this).parse()

private class ScriptValueTextParser(private val source: String) {
    private var offset = 0

    fun parse(): ScriptValue {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (!isEnd()) fail("Unexpected character '${peek()}'")
        return value
    }

    private fun parseValue(): ScriptValue {
        skipWhitespace()
        if (isEnd()) fail("Expected value")
        return when (val char = peek()) {
            '{' -> parseTable()
            '\'', '"' -> SString(parseString())
            else -> when {
                char == '+' || char == '-' || char == '.' || char.isDigit() -> parseNumber()
                char.isLetter() || char == '_' -> when (val identifier = parseIdentifier()) {
                    "true" -> SBool(true)
                    "false" -> SBool(false)
                    "nil" -> SNil
                    else -> fail("Unsupported literal '$identifier'")
                }
                else -> fail("Expected Lua value")
            }
        }
    }

    private fun parseTable(): ScriptValue {
        expect('{')
        val entries = linkedMapOf<ScriptValue, ScriptValue>()
        var implicitIndex = 1

        skipWhitespace()
        while (!takeIf('}')) {
            val start = offset
            val identifier = parseIdentifierOrNull()
            skipWhitespace()

            val key: ScriptValue
            val value: ScriptValue
            if (identifier != null && takeIf('=')) {
                key = SString(identifier)
                value = parseValue()
            } else {
                offset = start
                skipWhitespace()
                if (takeIf('[')) {
                    key = parseValue()
                    skipWhitespace()
                    expect(']')
                    skipWhitespace()
                    expect('=')
                    value = parseValue()
                } else {
                    key = SInt(implicitIndex++)
                    value = parseValue()
                }
            }
            entries[key] = value

            skipWhitespace()
            if (takeIf('}')) break
            if (!takeIf(',') && !takeIf(';')) fail("Expected ',', ';' or '}'")
            skipWhitespace()
            if (takeIf('}')) break
        }

        val indexes = entries.keys.mapNotNull { (it as? SInt)?.value }.sorted()
        return if (indexes.size == entries.size && indexes == (1..entries.size).toList()) {
            SList(indexes.map { entries.getValue(SInt(it)) })
        } else {
            STable(entries)
        }
    }

    private fun parseNumber(): ScriptValue {
        val start = offset
        if (peekOrNull() == '+' || peekOrNull() == '-') offset++

        var hasDigits = consumeDigits()
        var isFloatingPoint = false
        if (peekOrNull() == '.') {
            isFloatingPoint = true
            offset++
            hasDigits = consumeDigits() || hasDigits
        }
        if (!hasDigits) fail("Invalid number", start)

        if (peekOrNull() == 'e' || peekOrNull() == 'E') {
            isFloatingPoint = true
            offset++
            if (peekOrNull() == '+' || peekOrNull() == '-') offset++
            if (!consumeDigits()) fail("Invalid number exponent")
        }

        val literal = source.substring(start, offset)
        return if (isFloatingPoint) {
            SNumber(literal.toDoubleOrNull() ?: fail("Invalid number '$literal'", start))
        } else {
            SInt(literal.toIntOrNull() ?: fail("Integer '$literal' is out of range", start))
        }
    }

    private fun parseString(): String {
        val quote = peek()
        offset++
        val result = StringBuilder()
        while (!isEnd()) {
            val char = source[offset++]
            when {
                char == quote -> return result.toString()
                char != '\\' -> result.append(char)
                isEnd() -> fail("Unterminated escape sequence")
                else -> result.append(
                    when (val escaped = source[offset++]) {
                        'a' -> '\u0007'
                        'b' -> '\b'
                        'f' -> '\u000C'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'v' -> '\u000B'
                        '\\' -> '\\'
                        '\'' -> '\''
                        '"' -> '"'
                        else -> fail("Unsupported escape sequence \\$escaped")
                    }
                )
            }
        }
        fail("Unterminated string", offset)
    }

    private fun parseIdentifier(): String = parseIdentifierOrNull() ?: fail("Expected identifier")

    private fun parseIdentifierOrNull(): String? {
        if (isEnd() || (!peek().isLetter() && peek() != '_')) return null
        val start = offset++
        while (!isEnd() && (peek().isLetterOrDigit() || peek() == '_')) offset++
        return source.substring(start, offset)
    }

    private fun consumeDigits(): Boolean {
        val start = offset
        while (!isEnd() && peek().isDigit()) offset++
        return offset > start
    }

    private fun skipWhitespace() {
        while (!isEnd() && peek().isWhitespace()) offset++
    }

    private fun expect(expected: Char) {
        if (!takeIf(expected)) fail("Expected '$expected'")
    }

    private fun takeIf(expected: Char): Boolean {
        if (peekOrNull() != expected) return false
        offset++
        return true
    }

    private fun isEnd() = offset >= source.length
    private fun peek() = source[offset]
    private fun peekOrNull() = source.getOrNull(offset)
    private fun fail(message: String, at: Int = offset): Nothing = throw ScriptValueParseException(message, at)
}
