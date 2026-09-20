package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.ScriptBindings
import org.lain.engine.player.account.SessionTicketDto
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.data.PersistentId
import org.lain.engine.player.character.CharacterId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class SetSpeedIntentionPacket(
    val value: Float
) : Packet

val SERVERBOUND_SPEED_INTENTION_PACKET = Endpoint<SetSpeedIntentionPacket>()

// Chat

@Serializable
data class VolumePacket(
    val volume: Float
) : Packet

val SERVERBOUND_VOLUME_PACKET = Endpoint<VolumePacket>()

// Interactions

@Serializable
data class InteractionSelectionSelectPacket(val variantId: String?) : Packet

val SERVERBOUND_INTERACTION_SELECTION_SELECT_ENDPOINT = Endpoint<InteractionSelectionSelectPacket>()

@Serializable
data class InputPacket(
    val tick: Long,
    val actions: Set<InputAction>
) : Packet

val SERVERBOUND_INPUT_PACKET = Endpoint<InputPacket>()

// Inventory

@Serializable
data class CursorItemPacket(val item: PersistentId?) : Packet

val SERVERBOUND_CURSOR_ITEM_ENDPOINT = Endpoint<CursorItemPacket>()

// Arm

@Serializable
data class ArmStatusPacket(val extend: Boolean) : Packet

val SERVERBOUND_ARM_STATUS_ENDPOINT = Endpoint<ArmStatusPacket>()

// Scripts

@Serializable
data class ScriptBindingsPacket(val bindings: ScriptBindings) : Packet

val SERVERBOUND_SCRIPT_BINDINGS_ENDPOINT = Endpoint<ScriptBindingsPacket>()

// Character

@Serializable
data class CharacterApplyPacket(
    val characterId: CharacterId,
    val character: EngineCharacter? = null,
    val sessionTicket: SessionTicketDto? = null,
    val requestId: Long,
) : Packet

val SERVERBOUND_CHARACTER_APPLY_ENDPOINT = Endpoint<CharacterApplyPacket>()

@Serializable
data class LookApplyPacket(
    val lookId: String,
    val requestId: Long,
) : Packet

val SERVERBOUND_LOOK_APPLY_ENDPOINT = Endpoint<LookApplyPacket>()
