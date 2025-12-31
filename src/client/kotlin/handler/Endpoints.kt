package org.lain.engine.client.handler

import org.lain.engine.chat.MessageAuthor
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.EngineClient
import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_FULL_PLAYER_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_JOIN_GAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_DESTROY_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_JOIN_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SPEED_INTENTION_PACKET
import org.slf4j.Logger

fun ClientHandler.runEndpoints(clientAcknowledgeHandler: ClientAcknowledgeHandler, ) {
    clientAcknowledgeHandler.run()

    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { ctx ->
        taskExecutor.add("join_game") { applyJoinGame(playerData, worldData, setupData) }
    }

    // Players

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT) {
        updatePlayer(id) { applyPlayerAttributeUpdate(it) }
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
                    sourcePosition,
                ),
                channel,
                mentioned,
                speech,
                volume,
                placeholders,
                isSpy,
                heads,
                id
            )
        )
    }

    registerGameSessionReceiver(CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        applyDeleteChatMessage(message)
    }
}