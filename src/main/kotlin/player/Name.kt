package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.require

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
        val name = "$value<reset>"
        markUpdate(PlayerUpdate.CustomName(name))
        get<DisplayName>()?.custom = name
    }

val Player.displayName
    get() = this.require<DisplayName>().name

val Player.username
    get() = this.require<DisplayName>().username.value ?: "Unknown name"