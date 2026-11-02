package org.lain.engine.client.chat

import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.handler.ServerPlayState
import kotlin.math.pow

const val VOWELS = "аеёиоуыэюяaeiou"
fun Char.isVowel() = lowercaseChar() in VOWELS

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
                    buffer.count() > 4 ||
                    separator != isSeparator ||
                            (separator && codePoint != separatorCodePoint) ||
                            (!separator && hasVowel && !vowel)
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
        add(Syllable(startChars.joinToString(separator = ""), 0, TYPEWRITER_MIN_DELAY))
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
                    else -> delay
                }
            }

            is TextSegment.Number -> delay * 3f
        }

        add(
            Syllable(
                segment.text,
                segment.index,
                segmentDelay
            )
        )
    }
}

fun List<Syllable>.toChars(processed: Int = 0): List<SyllableChar> {
    require(processed in 0..size)

    var delay = 0f
    return flatMapIndexed { index, syllable ->
        if (index < processed) {
            syllable.chars.map { SyllableChar(syllable, CHAR_SHAKE_STOP_DELAY) }
        } else {
            syllable.chars.map {
                delay += syllable.charDur
                SyllableChar(syllable, delay)
            }
        }
    }
}

data class Syllable(
    val chars: String,
    val startCharIndex: Int,
    var delay: Float,
    val charsCount: Int = chars.codePointCount(0, chars.length),
    val charDur: Float = delay / charsCount,
)

data class SyllableChar(
    val parent: Syllable,
    var delay: Float
) {
    val shake: Float
        get() {
            if (delay <= CHAR_SHAKE_STOP_DELAY) return 0f

            val duration = parent.charDur - CHAR_SHAKE_STOP_DELAY
            val progress = ((delay - CHAR_SHAKE_STOP_DELAY) / duration).coerceIn(0f, 1f)
            return MAX_CHAR_SHAKE * progress
        }
}

data class TypewriterEffect(
    val lines: List<Line>,
    val options: Options
) {
    val ended get() = lines.all { it.processed == it.syllables.size }

    data class Options(
        val speed: Float = 0.6f
    ) {
        val syllableDur get() = TYPEWRITER_MAX_DELAY - (speed * (TYPEWRITER_MAX_DELAY - TYPEWRITER_MIN_DELAY))
    }

    data class Line(
        val syllables: List<Syllable>,
        val chars: List<SyllableChar>,
        var processed: Int,
    )
}

const val TYPEWRITER_MAX_DELAY = 3f // in delta ticks
const val TYPEWRITER_MIN_DELAY = 0.2f
const val CHAR_SHAKE_STOP_DELAY = -1.5f
const val MAX_CHAR_SHAKE = 0.8f
