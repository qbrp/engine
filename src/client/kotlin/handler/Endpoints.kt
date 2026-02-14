package org.lain.engine.client.handler

import org.lain.engine.chat.MessageAuthor
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.transport.packet.*
import org.lain.engine.util.Timestamp

fun ClientHandler.runEndpoints(clientAcknowledgeHandler: ClientAcknowledgeHandler, ) {
    clientAcknowledgeHandler.run()

    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { ctx ->
        taskExecutor.add("join_game") { applyJoinGame(playerData, worldData, setupData) }
    }

    // Players

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT) {
        updatePlayer(id) { applyPlayerAttributeUpdate(it, speed, jumpStrength) }
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT) {
        updatePlayer(id) { applyPlayerCustomName(it, name) }
    }

    registerGameSessionReceiver(CLIENTBOUND_SPEED_INTENTION_PACKET) {
        updatePlayerDetailed(id) { applyPlayerSpeedIntention(it, speedIntention) }
    }

    registerGameSessionReceiver(CLIENTBOUND_FULL_PLAYER_ENDPOINT) {
        updatePlayer(id) { applyFullPlayerData(it, data) }
    }

    // Join / Destroy

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_JOIN_ENDPOINT) {
        applyPlayerJoined(player)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_DESTROY_ENDPOINT) {
        updatePlayer(playerId) { applyPlayerDestroyed(it) }
    }

    // Other

    registerGameSessionReceiver(CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT) {
        applyServerSettingsUpdate(settings)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT) {
        applyNotification(type, once)
    }

    // Chat

    registerGameSessionReceiver(CLIENTBOUND_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        val world = gameSession.world
        if (world.id != sourceWorld) {
            LOGGER.error("Пропущено сообщение из-за отсутствия мира источника сообщения $sourceWorld")
            return@registerGameSessionReceiver
        }
        val player = sourcePlayer?.let { gameSession.getPlayer(it) }
        applyChatMessage(
            OutcomingMessage(
                text,
                MessageSource(
                    world,
                    MessageAuthor(
                        sourceAuthorName,
                        player
                    ),
                    // FIXME: Передавать в пакете время таймстамп
                    Timestamp(),
                    sourcePosition,
                ),
                channel,
                mentioned,
                notify,
                speech,
                volume,
                placeholders,
                isSpy,
                heads,
                color,
                id
            )
        )
    }

    registerGameSessionReceiver(CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        applyDeleteChatMessage(message)
    }

    registerGameSessionReceiver(CLIENTBOUND_ITEM_ENDPOINT) { gameSession ->
        applyItemPacket(item)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INTERACTION_PACKET) { gameSession ->
        updatePlayer(playerId) {
            applyInteractionPacket(
                it,
                interaction.toDomain(gameSession.itemStorage) ?: return@updatePlayer
            )
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_SOUND_PLAY_ENDPOINT) { gameSession ->
        applyPlaySoundPacket(play)
    }

    registerGameSessionReceiver(CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT) { gameSession ->
        applyContentsUpdatePacket()
    }

    registerGameSessionReceiver(CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET) { gameSession ->
        applyAcousticDebugVolumePacket(volumes)
    }
}