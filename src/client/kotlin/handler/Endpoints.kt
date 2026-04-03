package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.server.*
import org.lain.engine.transport.packet.*
import org.lain.engine.world.EngineChunk

fun ClientHandler.runEndpoints(clientAcknowledgeHandler: ClientAcknowledgeHandler, ) {
    clientAcknowledgeHandler.run()

    CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { ctx ->
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
        applyChatMessage(message)
    }

    registerGameSessionReceiver(CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        applyDeleteChatMessage(message)
    }

    registerGameSessionReceiver(CLIENTBOUND_ITEM_ENDPOINT) { gameSession ->
        applyItemPacket(item)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INTERACTION_PACKET) { gameSession ->
        updatePlayerDetailed(playerId) {
            applyInteractionPacket(it, interaction)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_INTERACTION_SELECTION_ENDPOINT) { gameSession ->
        applyInteractionSelectionPacket(selection)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INTERACTION_SELECTION_SELECT_ENDPOINT) { gameSession ->
        updatePlayerDetailed(player) {
            applyPlayerInteractionSelectionSelectPacket(it, variantId)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_INPUT_PACKET) { gameSession ->
        updatePlayerDetailed(playerId) {
            applyPlayerInputPacket(it, actions)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_SOUND_PLAY_ENDPOINT) { gameSession ->
        applyPlaySoundPacket(play, context)
    }

    registerGameSessionReceiver(CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT) { gameSession ->
        gameSession.onContentsUpdated()
    }

    registerGameSessionReceiver(CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET) { gameSession ->
        applyAcousticDebugVolumePacket(volumes)
    }

    registerGameSessionReceiver(CLIENTBOUND_VOXEL_EVENT_PACKET) { gameSession ->
        applyVoxelEvent(event)
    }

    CLIENTBOUND_CHUNK_ENDPOINT.registerClientReceiver { ctx ->
        taskExecutor.add("chunk-load") { applyChunkPacket(pos, EngineChunk(decals.toMutableMap(), hints.toMutableMap())) }
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_ENDPOINT) { gameSession ->
        applyEntity(persistentId, components)
    }

    registerPlayerSynchronizerEndpoint(PLAYER_ARM_STATUS_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_CUSTOM_NAME_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_SPEED_INTENTION_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_NARRATION_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_ATTRIBUTES_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_EQUIPMENT_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_MODEL_SYNCHRONIZER)
    registerPlayerSynchronizerEndpoint(PLAYER_HEARING_SYNCHRONIZER)
    registerItemSynchronizerEndpoint(ITEM_WRITABLE_SYNCHRONIZER)
    registerItemSynchronizerEndpoint(ITEM_GUN_SYNCHRONIZER)
    registerItemSynchronizerEndpoint(ITEM_FLASHLIGHT_SYNCHRONIZER)
}