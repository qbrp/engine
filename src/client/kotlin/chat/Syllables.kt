package org.lain.engine.client.chat

import net.minecraft.util.FormattedCharSequence

const val VOWELS = "аеёиоуыэюяaeiouy"

fun Char.isVowel() = lowercaseChar() in VOWELS

data class Syllable(
    val chars: String,
    val startCharIndex: Int,
    var delay: Float,
    val charsCount: Int = chars.codePointCount(0, chars.length),
    val charDur: Float = delay / charsCount,
    val shake: Boolean,
    val playSound: Boolean,
)

sealed interface TextSegment {
    val text: String
    val index: Int

    data class Number(
        override val text: String,
        override val index: Int
    ) : TextSegment

    data class Syllable(
        override val text: String,
        override val index: Int
    ) : TextSegment

    data class Separator(
        override val text: String,
        override val index: Int
    ) : TextSegment
}

fun FormattedCharSequence.toSegments(
    excludeStart: Int = 0
): List<TextSegment> = buildList {
    val chars = mutableListOf<String>()

    accept { _, _, codePoint ->
        chars += String(Character.toChars(codePoint))
        true
    }

    val buffer = StringBuilder()
    var start = excludeStart.coerceIn(0, chars.size)
    var hasVowel = false
    var isSeparator = false
    var isNumber = false
    var separatorCodePoint = -1

    fun flush() {
        if (buffer.isEmpty()) return

        val text = buffer.toString()

        add(
            if (isSeparator) {
                TextSegment.Separator(text, start)
            } else if (isNumber) {
                TextSegment.Number(text, start)
            } else {
                TextSegment.Syllable(text, start)
            }
        )

        buffer.clear()
        hasVowel = false
        isNumber = false
    }

    for (i in start until chars.size) {
        val char = chars[i]
        val codePoint = char.codePointAt(0)

        if (Character.isDigit(codePoint)) {
            buffer.append(char)
            isNumber = true
            isSeparator = false
            flush()
            start = i
            continue
        }

        val separator = !Character.isLetter(codePoint)
        val vowel = char.singleOrNull()?.isVowel() == true

        if (
            buffer.isNotEmpty() && (
                    separator != isSeparator ||
                            (separator && codePoint != separatorCodePoint) ||
                            (!separator && hasVowel && !vowel) ||
                            (!separator && buffer.count() > 4 )
                    )
        ) {
            flush()
            start = i
        }

        isSeparator = separator
        separatorCodePoint = if (separator) codePoint else -1

        buffer.append(char)

        if (vowel) hasVowel = true
    }

    flush()
}

fun FormattedCharSequence.toSyllables(
    delay: Float,
    excludeStart: Int = 0
): List<Syllable> = buildList {
    if (excludeStart > 0) {
        var i = 0
        val startChars = mutableListOf<Char>()
        accept { _, style, codePoint ->
            Character.toChars(codePoint).forEach { startChars.add(it) }
            i++
            i < excludeStart
        }
        add(
            Syllable(
                startChars.joinToString(separator = ""),
                0,
                VOICE_BLIP_MIN_DELAY,
                shake = false,
                playSound = false,
            )
        )
    }

    for (segment in toSegments(excludeStart)) {
        val segmentDelay = when (segment) {
            is TextSegment.Syllable -> delay

            is TextSegment.Separator -> {
                val text = segment.text

                when {
                    '.' in text -> delay * 3f * text.count()
                    text.any { it in "!?" } -> delay * 3f
                    text.any { it in ",;:" } -> delay * 2f
                    text.all { it.isWhitespace() } -> delay / 2.5f
                    else -> delay
                }
            }

            is TextSegment.Number -> delay * 3f
        }

        add(
            Syllable(
                segment.text,
                segment.index,
                segmentDelay,
                shake = true,
                playSound = segment !is TextSegment.Separator,
            )
        )
    }
}
