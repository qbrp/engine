package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.chat.CHAT_HEADS_PERMISSION
import org.lain.engine.util.Component
import org.lain.engine.util.apply
import org.lain.engine.util.let
import org.lain.engine.util.require

@Serializable
data class PlayerChatHeadsComponent(var enabled: Boolean) : Component

val Player.chatHeadsEnabled
    get() = !this.hasPermission(CHAT_HEADS_PERMISSION) || this.require<PlayerChatHeadsComponent>().enabled

fun Player.toggleChatHeads(): Boolean {
    return this.let<PlayerChatHeadsComponent, Boolean> {
        enabled = !enabled
        enabled
    }
}
