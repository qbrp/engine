package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get

@JvmInline
@Serializable
value class Username(val value: String) {
    init {
        require(value.none { it.isWhitespace() })
    }

    override fun toString(): String = value
}

@Serializable
data class DisplayName(
    val username: Username,
    var custom: String? = null
) : Component {
    val name: String
        get() = custom ?: username.value
}

fun Player.removeCustomName() {
    get<DisplayName>()?.custom = null
}

var Player.customName
    get() = get<DisplayName>()?.custom
    set(value) {
        markUpdate(PlayerUpdate.CustomName(value))
        get<DisplayName>()?.custom = value
    }

val Player.displayName
    get() = get<DisplayName>()?.name ?: "Unknown name"

val Player.username
    get() = get<DisplayName>()?.username?.value ?: "Unknown name"