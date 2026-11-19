package org.lain.engine.client.handler

import org.lain.engine.client.resources.LOGGER
import org.lain.engine.transport.packet.*

fun SessionPacketReceiver.runEndpoints() {
    // Join / Destroy

    registerGameSessionReceiver(CLIENTBOUND_ONLINE_PLAYER_ADD_ENDPOINT) { gameSession ->
        gameSession.handler.applyOnlinePlayerAdd(player, gameSession)
    }

    registerGameSessionReceiver(CLIENTBOUND_ONLINE_PLAYER_REMOVE_ENDPOINT) { gameSession ->
        gameSession.handler.applyOnlinePlayerRemove(gameSession, playerId)
    }

    // Other

    registerGameSessionReceiver(CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT) { gameSession ->
        gameSession.handler.applyServerSettingsUpdate(settings)
    }

    registerGameSessionReceiver(CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT) { gameSession ->
        gameSession.handler.applyNotification(type, once)
    }

    // Chat

    registerGameSessionReceiver(CLIENTBOUND_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        val world = gameSession.world
        val sourceWorld = message.source.world.id
        if (world.id != sourceWorld) {
            LOGGER.error("Пропущено сообщение из-за отсутствия мира источника сообщения $sourceWorld")
            return@registerGameSessionReceiver
        }
        gameSession.handler.applyChatMessage(gameSession, message)
    }

    registerGameSessionReceiver(CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT) { gameSession ->
        gameSession.handler.applyDeleteChatMessage(message)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT) { gameSession ->
        gameSession.handler.applyChatTypingStart(player, channel)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT) { gameSession ->
        gameSession.handler.applyChatTypingStop(player)
    }

    // Other

    registerGameSessionReceiver(CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT) { gameSession ->
        if (scope == null) {
            gameSession.recompile()
        } else {
            gameSession.luaContext.reloadScript(scope!!)
        }
    }

    registerGameSessionReceiver(CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET) { gameSession ->
        gameSession.handler.applyAcousticDebugVolumePacket(volumes)
    }

    registerGameSessionReceiver(CLIENTBOUND_VOXEL_EVENT_PACKET) { gameSession ->
        gameSession.handler.applyVoxelEvent(event)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHUNK_ENDPOINT) { gameSession ->
        gameSession.handler.applyChunkPacket(gameSession, chunk)
    }

    registerGameSessionReceiver(CLIENTBOUND_REPLICATION_ENDPOINT) { gameSession ->
        gameSession.handler.applyReplicationPacket(gameSession, this)
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_DEBUG_DATA_ENDPOINT) { gameSession ->
        gameSession.handler.applyEntityDebugData(gameSession, persistentId, data)
    }

    registerGameSessionReceiver(CLIENTBOUND_ENTITY_INSPECTION_ABORT_ENDPOINT) { gameSession ->
        gameSession.handler.applyInspectionAbort(gameSession, persistentId, reason)
    }

    registerGameSessionReceiver(CLIENTBOUND_CHARACTER_APPLY_CONFIRMATION_ENDPOINT) { gameSession ->
        gameSession.handler.applyCharacterApplyConfirmation(gameSession, requestId, errorMessage)
    }

    registerGameSessionReceiver(CLIENTBOUND_OPERATION_ENDPOINT) { gameSession ->
        gameSession.handler.applyOperation(dto, operation)
    }
}
