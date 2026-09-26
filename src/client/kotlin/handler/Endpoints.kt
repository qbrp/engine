package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.transport.packet.*

fun ClientHandler.runEndpoints() {
    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("join_game") { applyJoinGame(this) }
    }

    // Players

    registerGameSessionReceiver(CLIENTBOUND_FULL_PLAYER_ENDPOINT) { gameSession ->
        updatePlayer(id) { applyFullPlayerData(gameSession, it, data) }
    }

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

    registerGameSessionReceiver(CLIENTBOUND_SOUND_PLAY_ENDPOINT) { _ ->
        applyPlaySoundPacket(play, ignorePhysics)
    }

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

    CLIENTBOUND_CHUNK_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("chunk-load") { applyChunkPacket(chunk) }
    }

    registerGameSessionReceiver(CLIENTBOUND_REPLICATION_ENDPOINT) {
        applyReplicationFrame(it, frame)
    }

    registerGameSessionReceiver(CLIENTBOUND_OPERATION_ENDPOINT) { _ -> applyOperation(dto, operation) }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT) { _ ->
         applyEntityDebugData(data)
    }

    CLIENTBOUND_CHARACTER_APPLY_CONFIRMATION_ENDPOINT.registerClientReceiver {
        taskExecutor.add("character_apply_confirmation") { applyCharacterApplyConfirmation(requestId, errorMessage) }
    }
}
