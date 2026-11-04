package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.transport.packet.*

fun ClientHandler.runEndpoints() {
    // Join / Destroy

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_JOIN_ENDPOINT) { gameSession ->
        applyPlayerJoined(player, gameSession)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_DESTROY_ENDPOINT) { gameSession ->
        updatePlayer(playerId) { applyPlayerDestroyed(gameSession, it) }
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
        val sourceWorld = message.source.world.id
        if (world.id != sourceWorld) {
            LOGGER.error("Пропущено сообщение из-за отсутствия мира источника сообщения $sourceWorld")
            return@registerGameSessionReceiver
        }
        applyChatMessage(gameSession, message)
    }

    registerGameSessionReceiver(CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT) {
        applyDeleteChatMessage(message)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT) { gameSession ->
        applyChatTypingStart(player, channel)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT) {
        applyChatTypingStop(player)
    }

    // Other

    registerGameSessionReceiver(CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT) { gameSession ->
        if (scope == null) {
            gameSession.recompile()
        } else {
            gameSession.luaContext.reloadScript(scope!!)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET) { _ ->
        applyAcousticDebugVolumePacket(volumes)
    }

    registerGameSessionReceiver(CLIENTBOUND_VOXEL_EVENT_PACKET) { _ ->
        applyVoxelEvent(event)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHUNK_ENDPOINT) { gameSession ->
        applyChunkPacket(gameSession, chunk)
    }

    registerGameSessionReceiver(CLIENTBOUND_REPLICATION_ENDPOINT) { gameSession ->
        applyReplicationPacket(gameSession, this)
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT) { gameSession ->
         applyEntityDebugData(gameSession, persistentId, data)
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_INSPECTION_ABORT_ENDPOINT) { gameSession ->
        applyInspectionAbort(gameSession, persistentId, reason)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHARACTER_APPLY_CONFIRMATION_ENDPOINT) { gameSession ->
        applyCharacterApplyConfirmation(gameSession, requestId, errorMessage)
    }

    registerGameSessionReceiver(CLIENTBOUND_OPERATION_ENDPOINT) { _ -> applyOperation(dto, operation) }
}
