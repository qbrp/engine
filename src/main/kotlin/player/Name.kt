package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Color
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.require
import org.lain.engine.util.text.EngineText
import org.lain.engine.util.text.TextColor

@JvmInline
@Serializable
value class Username(val value: String) {
    init {
        require(value.none { it.isWhitespace() })
    }

    override fun toString(): String = value
}

const val CUSTOM_NAME_MAX_LENGTH = 32

fun String.isAlphaNumeric(): Boolean {
    val regex = Regex("^[А-Яа-яA-Za-z0-9]+$")
    return this.matches(regex)
}

class InvalidCustomNameException(message: String) : RuntimeException(message)

@Serializable
data class CustomName(
    val string: String,
    val color1: Color,
    val color2: Color? = null
) {
    init {
        require(string.length <= CUSTOM_NAME_MAX_LENGTH) { throw InvalidCustomNameException("Имя не должно превышать $CUSTOM_NAME_MAX_LENGTH символов") }
        require(string.isAlphaNumeric()) { throw InvalidCustomNameException("Имя не должно содержать специальные символы") }
    }

    val text by lazy { EngineText(string, TextColor(color1, color2)) }
}

@Serializable
data class DisplayName(
    val username: Username,
    var custom: CustomName? = null
) : Component {
    val usernameText by lazy { EngineText(username.value, TextColor(Color.WHITE)) }
}

fun EnginePlayer.removeCustomName() {
    get<DisplayName>()?.custom = null
}

var EnginePlayer.customName
    get() = get<DisplayName>()?.custom
    set(value) {
        get<DisplayName>()?.custom = value
    }

val EnginePlayer.displayName
    get() = this.require<DisplayName>().let { it.custom?.string ?: it.username.value }

val EnginePlayer.displayNameText
    get() = this.require<DisplayName>().let { it.custom?.text ?: it.usernameText }

val EnginePlayer.username
    get() = this.require<DisplayName>().username.value