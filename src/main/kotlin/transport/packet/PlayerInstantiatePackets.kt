package org.lain.engine.transport.packet

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.player.*
import org.lain.engine.player.account.SessionTicketDto
import org.lain.engine.player.character.*
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerId
import org.lain.engine.server.replication.EntityStateUpdate
import org.lain.engine.server.replication.collectReplicationEntities
import org.lain.engine.server.replication.fullReplicationUpdate
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

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


@ConsistentCopyVisibility
@Serializable
/**
 * Список игроков без адресата
 */
data class ClientboundPlayerList private constructor(val players: List<GeneralPlayerData>) {
    companion object {
        fun of(server: EngineServer, player: EnginePlayer): ClientboundPlayerList {
            return ClientboundPlayerList(
                server.playerStorage.all
                    .filter { it != player }
                    .map { GeneralPlayerData.of(it) }
            )
        }
    }
}

@Serializable
data class InitialReplicationState(
    val world: EntityStateUpdate.Full,
    val snapshots: Map<PersistentId, EntityStateUpdate.Full>
)

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
    val replicationSnapshot: InitialReplicationState,
    val character: ServerCharacterData?
) {
    val id
        get() = general.playerId

    companion object {
        fun of(player: EnginePlayer): ServerPlayerData = with(player.world)  {
            val movementStatus = player.require<MovementStatus>().copy()
            val voiceApparatus = player.require<VoiceApparatus>().copy()
            val defaults = player.require<DefaultPlayerAttributes>().copy()
            return ServerPlayerData(
                GeneralPlayerData.of(player),
                player.require<PlayerAttributes>().copy(),
                movementStatus.intention,
                movementStatus.stamina,
                voiceApparatus.inputVolume,
                voiceApparatus.minVolume ?: defaults.minVolume,
                voiceApparatus.maxVolume ?: defaults.maxVolume,
                voiceApparatus.baseVolume ?: defaults.playerBaseInputVolume,
                composeInitialReplicationState(player),
                player.get<AppliedCharacter>()?.character
            )
        }

        fun composeInitialReplicationState(player: EnginePlayer): InitialReplicationState = with(player.world) {
            val snapshots = player.collectReplicationEntities().associate { entity ->
                entity.persistentId() to entity.fullReplicationUpdate()
            }
            return InitialReplicationState(
                player.world.state.fullReplicationUpdate(),
                snapshots
            )
        }
    }
}

@Serializable
data class JoinGamePacket(
    val playerData: ServerPlayerData,
    val worldData: ClientboundWorldData,
    val setupData: ClientboundSetupData,
    val notifications: List<Notification>,
) : Packet {
    override val requireAuthorized: Boolean = false
}

@OptIn(ExperimentalSerializationApi::class)
val CLIENTBOUND_JOIN_GAME_ENDPOINT = Endpoint<JoinGamePacket>()

@Serializable
object ConfirmationPacket : Packet

val SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT = Endpoint<ConfirmationPacket>()

// General player data

@Serializable
data class GeneralPlayerData(
    val playerId: PlayerId,
    val displayName: DisplayName,
) {
    companion object {
        fun of(player: EnginePlayer): GeneralPlayerData = with(player.world) {
            return GeneralPlayerData(
                player.id,
                player.require<DisplayName>().copy()
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

// Namespaces

@Serializable
data class VerificationResponsePacket(
    val developerModeStatus: DeveloperModeStatus,
    val namespaces: NamespaceHashMap,
    val character: ServerboundSelectedCharacter?,
) : Packet {
    override val requireAuthorized: Boolean = false
}

val SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT = Endpoint<VerificationResponsePacket>()

@Serializable
data class GeneralServerData(
    val serverId: ServerId,
    val requireIdenticalNamespaces: Boolean,
    val canProvideCharacters: Boolean,
    val namespaceHashMap: NamespaceHashMap
)

@Serializable
data class VerificationDataPacket(val server: GeneralServerData) : Packet {
    override val requireAuthorized: Boolean = false
}

val CLIENTBOUND_VERIFICATION_ENDPOINT = Endpoint<VerificationDataPacket>()
