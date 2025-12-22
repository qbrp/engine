package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.DisplayName
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerId
import org.lain.engine.player.VoiceApparatus
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
    val playerSynchronizationRadius: Int,
    val defaultAttributesData: ClientDefaultAttributes,
    val serverId: ServerId,
    val playerList: ClientboundPlayerList,
    val chatSettings: ClientChatSettings
) {
    companion object {
        fun create(server: EngineServer, player: Player): ClientboundSetupData {
            return ClientboundSetupData(
                server.globals.playerSynchronizationRadius,
                ClientDefaultAttributes.of(server.globals.defaultPlayerAttributes),
                server.globals.serverId,
                ClientboundPlayerList.of(server),
                server.chat.settings.let {
                    ClientChatSettings.of(server.chat.settings, player)
                }
            )
        }
    }
}

@Serializable
data class ClientboundPlayerList(val players: List<GeneralPlayerData>) {
    companion object {
        fun of(server: EngineServer): ClientboundPlayerList {
            return ClientboundPlayerList(server.playerStorage.map { GeneralPlayerData.of(it) })
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
    val baseVolume: Float
) {
    companion object {
        fun of(player: Player): ServerPlayerData {
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
                voiceApparatus.baseVolume ?: defaults.playerBaseInputVolume
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
) {
    companion object {
        fun of(player: Player) = FullPlayerData(
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
        fun of(player: Player): GeneralPlayerData {
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