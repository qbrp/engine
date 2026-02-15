package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.Interaction
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.Storage

@Serializable
data class SetSpeedIntentionPacket(
    val value: Float
) : Packet

val SERVERBOUND_SPEED_INTENTION_PACKET = Endpoint<SetSpeedIntentionPacket>()

@Serializable
data class DeveloperModePacket(
    val enabled: Boolean,
    val acoustic: Boolean
) : Packet

val SERVERBOUND_DEVELOPER_MODE_PACKET = Endpoint<DeveloperModePacket>()

@Serializable
data class VolumePacket(
    val volume: Float
) : Packet

val SERVERBOUND_VOLUME_PACKET = Endpoint<VolumePacket>()

// Interactions

@Serializable
data class InteractionPacket(val interaction: PacketInteractionData) : Packet

@Serializable
sealed class PacketInteractionData {
    @Serializable
    data class SlotClick(val cursorItem: ItemUuid, val item: ItemUuid) : PacketInteractionData()
    @Serializable
    object RightClick : PacketInteractionData()
    @Serializable
    object LeftClick : PacketInteractionData()

    fun toDomain(itemStorage: Storage<ItemUuid, EngineItem>, notFound: (ItemUuid) -> Unit = {}): Interaction? {
        return when(this) {
            LeftClick -> Interaction.LeftClick
            RightClick -> Interaction.RightClick
            is SlotClick -> Interaction.SlotClick(
                itemStorage.get(cursorItem) ?: return null,
                itemStorage.get(item) ?: return null,
            )
        }
    }

    companion object {
        fun from(interaction: Interaction) = when(interaction) {
            is Interaction.LeftClick -> LeftClick
            is Interaction.RightClick -> RightClick
            is Interaction.SlotClick -> SlotClick(interaction.cursorItem.uuid, interaction.item.uuid)
        }
    }
}

val SERVERBOUND_INTERACTION_ENDPOINT = Endpoint<InteractionPacket>()

// Inventory

@Serializable
data class PlayerCursorItemPacket(val item: ItemUuid?) : Packet

val SERVERBOUND_PLAYER_CURSOR_ITEM_ENDPOINT = Endpoint<PlayerCursorItemPacket>()

// Arm

@Serializable
data class PlayerArmPacket(val extend: Boolean) : Packet

val SERVERBOUND_PLAYER_ARM_ENDPOINT = Endpoint<PlayerArmPacket>()