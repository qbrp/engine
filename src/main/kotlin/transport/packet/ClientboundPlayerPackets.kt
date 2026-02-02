package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import net.minecraft.network.PacketByteBuf
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.CustomName
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.Interaction
import org.lain.engine.player.MovementDefaultAttributes
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.player.PlayerId
import org.lain.engine.player.playerBaseInputVolume
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.Notification
import org.lain.engine.util.readPlayerId
import org.lain.engine.util.writePlayerId

///// Movement

// Speed Intention

@Serializable
data class PlayerSpeedIntentionPacket(
    val id: PlayerId,
    val speedIntention: Float
) : Packet

val CLIENTBOUND_SPEED_INTENTION_PACKET =
    Endpoint<PlayerSpeedIntentionPacket>()

///// Other

// Custom Name

@Serializable
data class PlayerNotificationPacket(
    val type: Notification,
    val once: Boolean
) : Packet

val CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT = Endpoint<PlayerNotificationPacket>()

@Serializable
data class PlayerCustomNamePacket(
    val id: PlayerId,
    val name: CustomName?
) : Packet

val CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT =
    Endpoint<PlayerCustomNamePacket>()

// Server Settings

@Serializable
data class ServerSettingsUpdatePacket(
    val settings: ClientboundServerSettings
) : Packet

val CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT = Endpoint<ServerSettingsUpdatePacket>()

// Attribute Update

data class PlayerAttributeUpdatePacket(
    val id: PlayerId,
    val speed: AttributeUpdate? = null,
    val jumpStrength: AttributeUpdate? = null
) : Packet

val CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT = Endpoint<PlayerAttributeUpdatePacket>(
    codec = PacketCodec.Binary(
        {
            PlayerAttributeUpdatePacket(
                readPlayerId(),
                readNullable { buf -> buf.readAttributeUpdate() },
                readNullable { buf -> buf.readAttributeUpdate() }
            )
        },
        {
            writePlayerId(it.id)
            writeNullable(it.speed) { buf, value -> buf.writeAttributeUpdate(value) }
            writeNullable(it.jumpStrength) { buf, value -> buf.writeAttributeUpdate(value) }
        }
    )
)

fun PacketByteBuf.writeAttributeUpdate(update: AttributeUpdate) {
    when(update) {
        AttributeUpdate.Reset -> writeString("RESET")
        is AttributeUpdate.Value -> {
            writeString("VALUE")
            writeFloat(update.value)
        }
    }
}

fun PacketByteBuf.readAttributeUpdate(): AttributeUpdate {
    return when(val str = readString()) {
        "RESET" -> AttributeUpdate.Reset
        "VALUE" -> AttributeUpdate.Value(readFloat())
        else -> throw IllegalStateException("Invalid attribute update value: $str")
    }
}

// Interactions

@Serializable
data class InteractionPacket(val interaction: ServerboundInteractionData) : Packet

@Serializable
sealed class ServerboundInteractionData {
    @Serializable
    data class SlotClick(val cursorItem: ItemUuid, val item: ItemUuid) : ServerboundInteractionData()
    @Serializable
    object RightClick : ServerboundInteractionData()
    @Serializable
    object LeftClick : ServerboundInteractionData()

    fun toDomain(itemStorage: ItemStorage) = when(this) {
        LeftClick -> Interaction.LeftClick
        RightClick -> Interaction.RightClick
        is SlotClick -> Interaction.SlotClick(
            itemStorage.get(cursorItem) ?: error("Item $cursorItem not found"),
            itemStorage.get(item) ?: error("Item $cursorItem not found")
        )
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