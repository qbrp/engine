package org.lain.engine.client.chat

import net.minecraft.util.FormattedCharSequence

const val VOWELS = "аеёиоуыэюя"
fun Char.isVowel() = lowercaseChar() in VOWELS

fun FormattedCharSequence.toSyllables(
    delay: Float,
    excludeStart: Int = 0
): List<Syllable> = buildList {
    val chars = mutableListOf<String>()

    accept { _, _, codePoint ->
        chars += String(Character.toChars(codePoint))
        true
    }
    
    val excluded = excludeStart.coerceIn(0, chars.size)
    if (excluded > 0) {
        add(Syllable(chars.take(excluded).joinToString(""), 0, delay))
    }

    // Изначально каждый слог содержит только гласную
    val ranges = mutableListOf<IntRange>()

    for (i in excluded until chars.size) {
        if (chars[i].singleOrNull()?.isVowel() == true) {
            ranges += i..i
        }
    }

    if (ranges.isEmpty()) {
        if (excluded < chars.size) {
            add(Syllable(chars.drop(excluded).joinToString(""), excluded, delay))
        }
        return@buildList
    }

    // Первый проход: расширяем каждый слог на один символ влево
    for (i in ranges.indices) {
        val range = ranges[i]
        val leftBoundary = if (i == 0) excluded else ranges[i - 1].last + 1

        if (range.first > leftBoundary) {
            ranges[i] = (range.first - 1)..range.last
        }
    }

    // Второй проход: распределяем оставшиеся символы
    for (i in ranges.indices) {
        val range = ranges[i]

        val leftBoundary = if (i == 0) excluded else ranges[i - 1].last + 1

        val rightBoundary = if (i == ranges.lastIndex) {
            chars.lastIndex
        } else {
            ranges[i + 1].first - 1
        }

        // Сначала расширяемся влево, затем вправо
        ranges[i] = leftBoundary..rightBoundary
    }

    for (range in ranges) {
        add(
            Syllable(
                chars.subList(range.first, range.last + 1).joinToString(""),
                range.first,
                delay
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
const val CHAR_SHAKE_STOP_DELAY = -2f
const val MAX_CHAR_SHAKE = 1f
