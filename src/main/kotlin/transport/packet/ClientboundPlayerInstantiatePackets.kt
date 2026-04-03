package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.lain.engine.container.getContainerItems
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.*
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerId
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.getEquipmentContainerSlots
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.PacketCodec
import org.lain.engine.util.component.require
import org.lain.engine.util.component.requireComponent
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.world

// Join Game

@Serializable
data class ClientboundWorldData(
    val id: WorldId
) {
    companion object {
        fun of(world: World) = ClientboundWorldData(world.id)
    }
}

@Serializable
data class ClientboundSetupData(
    val serverId: ServerId,
    val playerList: ClientboundPlayerList,
    val settings: ClientboundServerSettings
) {
    companion object {
        fun create(server: EngineServer, player: EnginePlayer): ClientboundSetupData {
            return ClientboundSetupData(
                server.globals.serverId,
                ClientboundPlayerList.of(server, player),
                ClientboundServerSettings.of(server, player)
            )
        }
    }
}

// References

@Serializable
data class PlayerReferencedItems(
    val inventory: List<ItemUuid>,
    val equipment: List<ItemUuid>
) {
    val all by lazy { inventory + equipment }

    companion object {
        fun of(player: EnginePlayer) = PlayerReferencedItems(
            player.items.map { it.uuid },
            player.world.getContainerItems(player.equipmentContainer).map { it.uuid }
        )
    }
}


@ConsistentCopyVisibility
@Serializable
/**
 * Список игроков без адресата
 */
data class ClientboundPlayerList private constructor(val players: List<GeneralPlayerData>) {
    companion object {
        fun of(server: EngineServer, player: EnginePlayer): ClientboundPlayerList {
            return ClientboundPlayerList(
                server.playerStorage
                    .filter { it != player }
                    .map { GeneralPlayerData.of(it) }
            )
        }
    }
}

@Serializable
data class ServerPlayerData(
    val general: GeneralPlayerData,
    val attributes: PlayerAttributes,
    val speedIntention: Float,
    val stamina: Float,
    val volume: Float,
    val minVolume: Float,
    val maxVolume: Float,
    val baseVolume: Float,
    val items: List<ClientboundItemData>,
    val equipment: Map<EquipmentSlot, ClientboundItemData>,
    val skinEyeY: Float
) {
    val id
        get() = general.playerId

    val allItems by lazy { items + equipment.values }

    val referencedItems by lazy {
        PlayerReferencedItems(
            items.map { it.uuid },
            equipment.values.map { it.uuid }
        )
    }

    companion object {
        fun of(player: EnginePlayer): ServerPlayerData {
            val movementStatus = player.require<MovementStatus>().copy()
            val voiceApparatus = player.require<VoiceApparatus>().copy()
            val defaults = player.require<DefaultPlayerAttributes>().copy()
            val world = player.world
            return ServerPlayerData(
                GeneralPlayerData.of(player),
                player.require<PlayerAttributes>().copy(),
                movementStatus.intention,
                movementStatus.stamina,
                voiceApparatus.inputVolume,
                voiceApparatus.minVolume ?: defaults.minVolume,
                voiceApparatus.maxVolume ?: defaults.maxVolume,
                voiceApparatus.baseVolume ?: defaults.playerBaseInputVolume,
                player.items.map { ClientboundItemData.from(player.world, it) },
                world
                    .getEquipmentContainerSlots(player.equipmentContainer)
                    .mapValues { (_, item) -> ClientboundItemData.from(world, item) },
                player.skinEyeY,
            )
        }
    }
}

@Serializable
data class JoinGamePacket(
    val playerData: ServerPlayerData,
    val worldData: ClientboundWorldData,
    val setupData: ClientboundSetupData,
    val notifications: List<Notification>
) : Packet

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_JOIN_GAME_ENDPOINT = Endpoint<JoinGamePacket>(
    PacketCodec.Kotlinx(
        JoinGamePacket.serializer(),
        ItemProtobuf
    ),
)

// Full player data (for synchronization)

@Serializable
data class FullPlayerPacket(
    val id: PlayerId,
    val data: FullPlayerData
) : Packet

@Serializable
data class FullPlayerData(
    val movementStatus: MovementStatus,
    val attributes: PlayerAttributes,
    val armStatus: ArmStatus,
    val skinEyeY: Float,
    val referencedItems: PlayerReferencedItems,
) {
    companion object {
        fun of(player: EnginePlayer) = FullPlayerData(
            player.require<MovementStatus>().copy(),
            player.require<PlayerAttributes>().copy(),
            player.require<ArmStatus>().copy(),
            player.skinEyeY,
            PlayerReferencedItems.of(player)
        )
    }
}

val CLIENTBOUND_FULL_PLAYER_ENDPOINT = Endpoint<FullPlayerPacket>()

// General player data

@Serializable
data class GeneralPlayerData(
    val playerId: PlayerId,
    val displayName: DisplayName,
    val equipmentContainer: PersistentId
) {
    companion object {
        fun of(player: EnginePlayer): GeneralPlayerData = with(player.world) {
            return GeneralPlayerData(
                player.id,
                player.require<DisplayName>().copy(),
                player.equipmentContainer.requireComponent<PersistentId>()
            )
        }
    }
}

val CLIENTBOUND_PLAYER_JOIN_ENDPOINT = Endpoint<PlayerJoinServerPacket>()

// Join / Leave

@Serializable
data class PlayerJoinServerPacket(
    val player: GeneralPlayerData
) : Packet

@Serializable
data class PlayerDestroyPacket(
    val playerId: PlayerId
) : Packet

val CLIENTBOUND_PLAYER_DESTROY_ENDPOINT = Endpoint<PlayerDestroyPacket>()