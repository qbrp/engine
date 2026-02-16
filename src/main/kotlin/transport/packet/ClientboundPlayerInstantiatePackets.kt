package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.ArmStatus
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.DisplayName
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerId
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.items
import org.lain.engine.player.playerBaseInputVolume
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerId
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.require
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
                server.playerStorage
                    .filter { it != player }
                    .map { GeneralPlayerData.of(it) }
            )
        }
    }
}

@Serializable
data class ServerPlayerData(
    val id: PlayerId,
    val displayName: DisplayName,
    val attributes: PlayerAttributes,
    val speedIntention: Float,
    val stamina: Float,
    val volume: Float,
    val minVolume: Float,
    val maxVolume: Float,
    val baseVolume: Float,
    val items: List<ClientboundItemData>
) {
    companion object {
        fun of(player: EnginePlayer): ServerPlayerData {
            val movementStatus = player.require<MovementStatus>()
            val voiceApparatus = player.require<VoiceApparatus>()
            val defaults = player.require<DefaultPlayerAttributes>()
            return ServerPlayerData(
                player.id,
                player.require(),
                player.require(),
                movementStatus.intention,
                movementStatus.stamina,
                voiceApparatus.inputVolume,
                voiceApparatus.minVolume ?: defaults.minVolume,
                voiceApparatus.maxVolume ?: defaults.maxVolume,
                voiceApparatus.baseVolume ?: defaults.playerBaseInputVolume,
                player.items.map { ClientboundItemData.from(it) }
            )
        }
    }
}

@Serializable
data class JoinGamePacket(
    val playerData: ServerPlayerData,
    val worldData: ClientboundWorldData,
    val setupData: ClientboundSetupData
) : Packet

val CLIENTBOUND_JOIN_GAME_ENDPOINT = Endpoint<JoinGamePacket>()

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
    val armStatus: ArmStatus
) {
    companion object {
        fun of(player: EnginePlayer) = FullPlayerData(
            player.require(),
            player.require(),
            player.require()
        )
    }
}

val CLIENTBOUND_FULL_PLAYER_ENDPOINT = Endpoint<FullPlayerPacket>()

// General player data

@Serializable
data class PlayerJoinServerPacket(
    val player: GeneralPlayerData
) : Packet

@Serializable
data class GeneralPlayerData(
    val playerId: PlayerId,
    val displayName: DisplayName
) {
    companion object {
        fun of(player: EnginePlayer): GeneralPlayerData {
            return GeneralPlayerData(
                player.id,
                player.require()
            )
        }
    }
}

val CLIENTBOUND_PLAYER_JOIN_ENDPOINT = Endpoint<PlayerJoinServerPacket>()

@Serializable
data class PlayerDestroyPacket(
    val playerId: PlayerId
) : Packet

val CLIENTBOUND_PLAYER_DESTROY_ENDPOINT = Endpoint<PlayerDestroyPacket>()