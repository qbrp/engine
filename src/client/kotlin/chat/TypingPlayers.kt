package org.lain.engine.client.chat

import net.minecraft.client.resources.PlayerSkin
import net.minecraft.network.chat.Component
import org.lain.engine.chat.ChannelId
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.LowDetail
import org.lain.engine.client.render.player.getSkin
import org.lain.engine.mc.displayNameText
import org.lain.engine.player.PlayerId
import org.lain.engine.player.require
import org.lain.engine.transport.packet.ClientChatChannel

data class TypingPlayer(
    val id: PlayerId,
    val skin: PlayerSkin?,
    val name: Component,
    val channel: ClientChatChannel
) {
    override fun equals(other: Any?): Boolean {
        return other is TypingPlayer && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

class TypingPlayers(private val gameSession: GameSession) {
    private val chatManager get() = gameSession.chatManager
    private val onlinePlayers get() = gameSession.onlinePlayers
    private val typing = mutableMapOf<PlayerId, TypingPlayer>()

    fun list(): Collection<TypingPlayer> = typing.values

    fun start(player: PlayerId, channel: ChannelId) {
        val channel = chatManager.availableChannels[channel] ?: return
        val enginePlayer = gameSession.playerStorage.get(player)?.takeIf { !it.require<LowDetail>().enabled }
        val onlinePlayer = onlinePlayers.get(player) ?: return
        val skin = enginePlayer?.getSkin()
        if (onlinePlayer.id != gameSession.mainPlayer.id || gameSession.client.developerMode) {
            val name = if (channel.speech || enginePlayer == null) {
                Component.literal(onlinePlayer.username.value)
            } else {
                enginePlayer.displayNameText
            }

            typing[player] = TypingPlayer(player, skin, name, channel)
        }
    }

    fun stop(player: PlayerId) {
        typing.remove(player)
    }
}