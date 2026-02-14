package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import net.minecraft.network.PacketByteBuf
import org.lain.engine.player.CustomName
import org.lain.engine.player.PlayerId
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.Notification
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.readPlayerId
import org.lain.engine.util.writePlayerId
import org.lain.engine.world.ImmutableVoxelPos

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

// Contents update

@Serializable
object ContentsUpdatePacket : Packet

val CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT = Endpoint<ContentsUpdatePacket>()

// Acoustic debug

@Serializable
data class AcousticDebugVolumesPacket(val volumes: List<Pair<ImmutableVoxelPos, Float>>) : Packet

val CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET = Endpoint<AcousticDebugVolumesPacket>()

// Interaction

@Serializable
data class PlayerInteractionPacket(val playerId: PlayerId, val interaction: PacketInteractionData) : Packet

val CLIENTBOUND_PLAYER_INTERACTION_PACKET = Endpoint<PlayerInteractionPacket>()