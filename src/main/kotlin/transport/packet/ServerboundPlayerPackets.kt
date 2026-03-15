package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.ItemUuid
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet

@Serializable
data class SetSpeedIntentionPacket(
    val value: Float
) : Packet

val SERVERBOUND_SPEED_INTENTION_PACKET = Endpoint<SetSpeedIntentionPacket>()

@Serializable
data class DeveloperModePacket(val status: DeveloperModeStatus) : Packet

@Serializable
data class DeveloperModeStatus(val enabled: Boolean = false, val acoustic: Boolean = false)

val SERVERBOUND_DEVELOPER_MODE_PACKET = Endpoint<DeveloperModePacket>()

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
    val actions: Set<InputActionDto>
) : Packet

val SERVERBOUND_INPUT_PACKET = Endpoint<InputPacket>()

@Serializable
object ClientTickEndPacket : Packet

val SERVERBOUND_CLIENT_TICK_END_ENDPOINT = Endpoint<ClientTickEndPacket>()

// Inventory

@Serializable
data class CursorItemPacket(val item: ItemUuid?) : Packet

val SERVERBOUND_CURSOR_ITEM_ENDPOINT = Endpoint<CursorItemPacket>()

// Arm

@Serializable
data class ArmStatusPacket(val extend: Boolean) : Packet

val SERVERBOUND_ARM_STATUS_ENDPOINT = Endpoint<ArmStatusPacket>()

// Contents

@Serializable
object ReloadContentsRequestPacket : Packet

val SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT = Endpoint<ReloadContentsRequestPacket>()

