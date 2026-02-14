package org.lain.engine.chat

import kotlin.random.Random

private val colorMap = mapOf(
    // ampersand codes
    "&0" to "#000000", "&1" to "#0000AA", "&2" to "#00AA00", "&3" to "#00AAAA",
    "&4" to "#AA0000", "&5" to "#AA00AA", "&6" to "#FFAA00", "&7" to "#AAAAAA",
    "&8" to "#555555", "&9" to "#5555FF", "&a" to "#55FF55", "&b" to "#55FFFF",
    "&c" to "#FF5555", "&d" to "#FF55FF", "&e" to "#FFFF55", "&f" to "#FFFFFF",
    "&r" to "reset",
    // named tags
    "<black>" to "#000000", "<dark_blue>" to "#0000AA", "<dark_green>" to "#00AA00",
    "<dark_aqua>" to "#00AAAA", "<dark_red>" to "#AA0000", "<dark_purple>" to "#AA00AA",
    "<gold>" to "#FFAA00", "<gray>" to "#AAAAAA", "<dark_gray>" to "#555555",
    "<blue>" to "#5555FF", "<green>" to "#55FF55", "<aqua>" to "#55FFFF",
    "<red>" to "#FF5555", "<light_purple>" to "#FF55FF", "<yellow>" to "#FFFF55",
    "<white>" to "#FFFFFF"
)

fun String.distort(strength: Float, artifacts: List<Char>): String {
    val result = StringBuilder()
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c.isLetter() && Random.nextFloat() < (strength / 2f)) {
            if (Random.nextBoolean() && i < lastIndex) {
                result.append(this[i + 1])
                result.append(c)
                i += 2
                continue
            } else {
                result.append(artifacts.random())
                i++
                continue
            }
        } else {
            result.append(c)
            i++
        }
    }
    return result.toString()
}

data class AcousticFormatting(val levels: List<AcousticLevel> = listOf()) {
    fun getLevel(volume: Float): AcousticLevel {
        var level = levels.first()
        for (lvl in levels) {
            val vol = lvl.volume
            if (volume >= vol) {
                level = lvl
            }
        }
        return level
    }
}

data class AcousticLevel(
    val volume: Float,
    val multiplier: Float = 1f,
    val inputPlaceholders: Map<String, String>,
    val outPlaceholders: Map<String, String>
)