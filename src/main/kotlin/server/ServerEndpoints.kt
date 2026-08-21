package org.lain.engine.server

import org.lain.engine.transport.packet.SERVERBOUND_ARM_STATUS_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_CHARACTER_APPLY_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_TYPING_END_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_TYPING_START_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_CURSOR_ITEM_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_ENTITY_COMPONENT_RPC_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_ENTITY_RESYNC_REQUEST_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_INPUT_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_LOOK_APPLY_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_SCRIPT_BINDINGS_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOXEL_BLOCK_HINT_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT

fun ServerHandler.registerEndpoints() {
    SERVERBOUND_SPEED_INTENTION_PACKET.registerReceiver { ctx ->
        onPlayerSpeedIntentionSet(
            ctx.sender,
            value
        )
    }
    SERVERBOUND_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx ->
        onChatMessage(
            ctx.sender,
            text,
            channel
        )
    }
    SERVERBOUND_DEVELOPER_MODE_PACKET.registerReceiver { ctx ->
        onDeveloperModeEnabled(
            ctx.sender,
            status.enabled,
            status.acoustic
        )
    }
    SERVERBOUND_VOLUME_PACKET.registerReceiver { ctx -> onPlayerVolume(ctx.sender, volume) }
    SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx ->
        onChatMessageDelete(
            ctx.sender,
            message
        )
    }
    SERVERBOUND_CURSOR_ITEM_ENDPOINT.registerReceiver { ctx ->
        onPlayerCursorItem(
            ctx.sender,
            item
        )
    }
    SERVERBOUND_CHAT_TYPING_START_ENDPOINT.registerReceiver { ctx ->
        onPlayerChatTypingStart(
            ctx.sender,
            channel
        )
    }
    SERVERBOUND_CHAT_TYPING_END_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingEnd(ctx.sender) }
    SERVERBOUND_ARM_STATUS_ENDPOINT.registerReceiver { ctx ->
        onPlayerArmStatus(
            ctx.sender,
            extend
        )
    }
    SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT.registerReceiver { ctx ->
        onWriteableContentsUpdate(
            ctx.sender,
            item,
            contents
        )
    }
    SERVERBOUND_INPUT_PACKET.registerReceiver { ctx ->
        onPlayerInput(
            ctx.sender,
            tick,
            actions
        )
    }
    SERVERBOUND_ENTITY_RESYNC_REQUEST_ENDPOINT.registerReceiver { ctx ->
        onEntityResyncRequest(ctx.sender, persistentId)
    }
    SERVERBOUND_VOXEL_BLOCK_HINT_PACKET.registerReceiver { ctx ->
        onVoxelBlockHint(
            ctx.sender,
            pos,
            action
        )
    }
    SERVERBOUND_SCRIPT_BINDINGS_ENDPOINT.registerReceiver { ctx ->
        onScriptBindings(
            ctx.sender,
            bindings
        )
    }
    SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.registerReceiver { ctx ->
        onPlayerInstantiationConfirm(
            ctx.sender
        )
    }
    SERVERBOUND_ENTITY_COMPONENT_RPC_ENDPOINT.registerReceiver { ctx ->
        onEntityComponentRpcPacket(
            ctx.sender,
            entity,
            delta
        )
    }
    SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT.registerReceiver { ctx ->
        onEntityDebugView(
            ctx.sender,
            persistentId
        )
    }
    SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT.registerReceiver { ctx ->
        onEntityDebugViewStop(
            ctx.sender
        )
    }
    SERVERBOUND_CHARACTER_APPLY_ENDPOINT.registerReceiver { ctx ->
        onCharacterApply(
            ctx.sender,
            characterId,
            character,
            sessionTicket?.map(),
            requestId
        )
    }
    SERVERBOUND_LOOK_APPLY_ENDPOINT.registerReceiver { ctx ->
        onLookApply(
            ctx.sender,
            lookId,
            requestId
        )
    }
}