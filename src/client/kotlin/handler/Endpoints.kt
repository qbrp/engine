package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.server.*
import org.lain.engine.transport.packet.*

fun ClientHandler.runEndpoints(clientAcknowledgeHandler: ClientAcknowledgeHandler) {
    clientAcknowledgeHandler.run()

    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("join_game") { applyJoinGame(this) }
    }

    // Players

    registerGameSessionReceiver(CLIENTBOUND_FULL_PLAYER_ENDPOINT) { gameSession ->
        updatePlayer(id) { applyFullPlayerData(gameSession, it, data) }
    }

    // Join / Destroy

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_JOIN_ENDPOINT) {
        applyPlayerJoined(player)
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

    registerGameSessionReceiver(CLIENTBOUND_WORLD_STATE_DELTA_PACKET) { gameSession ->
        applyWorldState(gameSession, snapshot)
    }

    CLIENTBOUND_CHUNK_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("chunk-load") { applyChunkPacket(chunk) }
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DELTA_ENDPOINT) {
        applyEntity(it, persistentId, snapshot)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INPUT_PROCESSED_ENDPOINT) {
        applyProcessedInput(it, processedInputTick)
    }

    registerGameSessionReceiver(CLIENTBOUND_INTENT_ENDPOINT) { _ -> applyIntent(dto, intent) }

    registerGameSessionReceiver(CLIENTBOUND_ITEM_UNLOAD_ENDPOINT, { it.endTickTaskExecutor }) {
        applyItemUnload(it, items)
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT) { _ ->
         applyEntityDebugData(data)
    }

    CLIENTBOUND_CHARACTER_APPLY_CONFIRMATION_ENDPOINT.registerClientReceiver {
        taskExecutor.add("character_apply_confirmation") { applyCharacterApplyConfirmation(requestId, errorMessage) }
    }
}
