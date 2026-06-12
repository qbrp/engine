package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.server.*
import org.lain.engine.transport.packet.*

fun ClientHandler.runEndpoints(clientAcknowledgeHandler: ClientAcknowledgeHandler) {
    clientAcknowledgeHandler.run()

    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("join_game") { applyJoinGame(playerData, worldData, setupData, notifications) }
    }

    // Players

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

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INTERACTION_PACKET) {
        updatePlayerDetailed(playerId) {
            applyInteractionPacket(it, interaction)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_INTERACTION_SELECTION_ENDPOINT) {
        applyInteractionSelectionPacket(selection)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INTERACTION_SELECTION_SELECT_ENDPOINT) {
        updatePlayerDetailed(player) {
            applyPlayerInteractionSelectionSelectPacket(it, variantId)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INPUT_PACKET) { _ ->
        updatePlayerDetailed(playerId) {
            applyPlayerInputPacket(it, actions)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_SOUND_PLAY_ENDPOINT) { _ ->
        applyPlaySoundPacket(play, context)
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

    registerGameSessionReceiver(CLIENTBOUND_DYNAMIC_VOXEL_DELTA_ENDPOINT) {
        applyDynamicVoxelDelta(it, voxelPos, components)
    }

    registerGameSessionReceiver(CLIENTBOUND_WORLD_STATE_DELTA_PACKET) { gameSession ->
        applyWorldState(gameSession, components)
    }

    CLIENTBOUND_CHUNK_ENDPOINT.registerClientReceiver { _ ->
        taskExecutor.add("chunk-load") { applyChunkPacket(chunk) }
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DELTA_ENDPOINT) {
        applyEntity(it, dto.persistentId, dto.components)
    }

    registerGameSessionReceiver(CLIENTBOUND_INTENT_ENDPOINT) { _ -> applyIntent(dto, intent) }

    registerGameSessionReceiver(CLIENTBOUND_ITEM_UNLOAD_ENDPOINT, { it.endTickTaskExecutor }) {
        applyItemUnload(it, items)
    }

    registerPlayerSynchronizerEndpoint(PLAYER_ARM_STATUS_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_SPEED_INTENTION_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_NARRATION_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_ATTRIBUTES_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_MODEL_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_HEARING_SYNCHRONIZER)
}