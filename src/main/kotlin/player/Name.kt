package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.character.CharacterDisplay
import org.lain.engine.util.Color
import org.lain.engine.world.World

@JvmInline
@Serializable
// Параметр может отличаться от реального имен игрока. Пример: ReplayViewer вместо Replay Viewer в Flashback
value class Username(val value: String) {
    init {
        require(value.none { it.isWhitespace() })
    }

    override fun toString(): String = value
}

const val CUSTOM_NAME_MAX_LENGTH = 32

private val FORBIDDEN_CHARS = setOf('<', '#', '/', '&')
class InvalidCustomNameException(message: String) : RuntimeException(message)

@Serializable
data class CustomName(
    val string: String,
    val color1: Color,
    val color2: Color? = null
) {
    val gradientText: List<ColoredChar>
        get() = gradientText(string, color1, color2 ?: color1)

    init {
        require(string.length <= CUSTOM_NAME_MAX_LENGTH) {
            throw InvalidCustomNameException("Имя не должно превышать $CUSTOM_NAME_MAX_LENGTH символов")
        }

        require(string.none { it in FORBIDDEN_CHARS }) {
            throw InvalidCustomNameException("Имя не должно содержать символы <, #, / и &")
        }
    }
}

data class ColoredChar(val char: Char, val color: Color)

fun gradientText(text: String, color1: Color, color2: Color): List<ColoredChar> {
    return text.mapIndexed { index, ch ->
        ColoredChar(ch, color1.blend(color2, index.toFloat() / text.length))
    }
}

@Serializable
data class DisplayName(
    val username: Username,
    var custom: CustomName? = null
) : Component {
    val gradientText: List<ColoredChar>
        get() {
            val color1 = custom?.color1 ?: Color.WHITE
            val color2 = custom?.color2 ?: color1
            return gradientText(custom?.string ?: username.value, color1, color2)
        }
}

fun EnginePlayer.removeCustomName() {
    get<DisplayName>()?.custom = null
}

val EnginePlayer.displayName: List<ColoredChar>
    get() = with(world) { entity.displayName() }

val EnginePlayer.displayNameString: String
    get() = with(world) { entity.displayNameString() }

val EnginePlayer.username: String
    get() = with(world) { entity.username() }

var EnginePlayer.customName
    get() = get<DisplayName>()?.custom
    set(value) {
        get<DisplayName>()?.custom = value
    }

context(world: World)
fun EntityId.displayName() = getComponent<CharacterDisplay>()?.name?.gradientChars ?: requireComponent<DisplayName>().gradientText

context(world: World)
fun EntityId.displayNameString() = displayName().joinToString(separator = "") { it.char.toString() }

context(world: World)
fun EntityId.username() = this.requireComponent<DisplayName>().username.value
