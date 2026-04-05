package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.chat.CHAT_HEADS_PERMISSION
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.let
import org.lain.cyberia.ecs.require

@Serializable
data class PlayerChatHeadsComponent(var enabled: Boolean) : Component

val EnginePlayer.chatHeadsEnabled
    get() = !this.hasPermission(CHAT_HEADS_PERMISSION) || this.require<PlayerChatHeadsComponent>().enabled

fun EnginePlayer.toggleChatHeads(): Boolean {
    return this.let<PlayerChatHeadsComponent, Boolean> {
        enabled = !enabled
        enabled
    }
}
