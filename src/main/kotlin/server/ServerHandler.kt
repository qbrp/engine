package org.lain.engine.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.player.DefaultPlayerAttributes
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.developerMode
import org.lain.engine.player.intentSpeed
import org.lain.engine.player.speak
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.packet.GlobalAcknowledgeListener
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_FULL_PLAYER_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_JOIN_GAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_DESTROY_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_JOIN_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.ClientboundServerSettings
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.transport.packet.OutcomingChatMessagePacket
import org.lain.engine.transport.packet.PlayerAttributeUpdatePacket
import org.lain.engine.transport.packet.PlayerCustomNamePacket
import org.lain.engine.transport.packet.PlayerNotificationPacket
import org.lain.engine.transport.packet.FullPlayerPacket
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.PlayerDestroyPacket
import org.lain.engine.transport.packet.PlayerJoinServerPacket
import org.lain.engine.transport.packet.PlayerSpeedIntentionPacket
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.transport.packet.ServerSettingsUpdatePacket
import org.lain.engine.util.ImmutableVec3
import org.lain.engine.util.Pos
import org.lain.engine.util.filterNearestPlayers
import org.lain.engine.util.require
import org.lain.engine.world.pos
import org.lain.engine.world.world
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

sealed class AttributeUpdate() {
    object Reset : AttributeUpdate()
    class Value(val value: Float) : AttributeUpdate()
}

enum class Notification {
    INVALID_SOURCE_POS,
}

class ServerHandler(
    private val playerStorage: PlayerStorage,
    private val server: EngineServer,
    private val transport: ServerTransportContext
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val globals: ServerGlobals
        get() = server.globals
    private val playerSynchronizationRadius
        get() = globals.playerSynchronizationRadius

    private var destroy = false
    private val queue = LinkedBlockingQueue<Runnable>()
    private val thread: Thread = thread(name = "Engine Packet sending", isDaemon = true) {
        while (true) {
            if (!destroy) {
                break
            }
            try {
                queue.poll()?.run()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private data class SynchronizationState(
        val player: Player,
        val synchronizedPlayers: MutableList<Player> = mutableListOf()
    )

    private val states = mutableMapOf<PlayerId, SynchronizationState>()

    private val Player.synchronizationState
        get() = states.computeIfAbsent(id) { SynchronizationState(this) }

    private fun updatePlayer(id: PlayerId, update: Player.() -> Unit) = execute {
        server.playerStorage.get(id)?.update()
    }

    private fun getPlayer(id: PlayerId): Player? {
        return server.playerStorage.get(id)
    }

    private fun execute(r: Runnable) = server.execute(r)

    fun run() {
        GlobalAcknowledgeListener.start()

        SERVERBOUND_SPEED_INTENTION_PACKET.registerReceiver { ctx -> onPlayerSpeedIntentionSet(ctx.sender, value) }
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx -> onChatMessage(ctx.sender, text, channel) }
        SERVERBOUND_DEVELOPER_MODE_PACKET.registerReceiver { ctx -> onDeveloperModeEnabled(ctx.sender, enabled) }
        SERVERBOUND_VOLUME_PACKET.registerReceiver { ctx -> onPlayerVolume(ctx.sender, volume) }
    }

    fun invalidate() {
        transport.unregisterAll()
        destroy = true
    }

    private fun onPlayerVolume(player: PlayerId, volume: Float) = updatePlayer(player) {
        this.require<VoiceApparatus>().inputVolume = volume
    }

    private fun onPlayerSpeedIntentionSet(player: PlayerId, value: Float) = updatePlayer(player) {
        intentSpeed(value.coerceIn(0f, 1f))
    }

    private fun onChatMessage(player: PlayerId, content: String, channelId: ChannelId) = updatePlayer(player) {
        val content = content.trim()
        if (content.isEmpty()) return@updatePlayer
        speak(content, channelId)
    }

    private fun onDeveloperModeEnabled(playerId: PlayerId, enabled: Boolean) = updatePlayer(playerId) {
        developerMode = enabled
    }

    fun synchronizePlayers() {
        for (player in playerStorage) {
            val state = player.synchronizationState
            val playersInRadius = player
                .filterNearestPlayers(globals.playerSynchronizationRadius)
            val playersToSync = playersInRadius
                .filter { it !in state.synchronizedPlayers && it.id != player.id }
            for (playerToSync in playersToSync) {
                state.synchronizedPlayers += playerToSync

                // Игрок будет кикнут, если состояние синхронизации не придёт
                coroutineScope.launch {
                    CLIENTBOUND_FULL_PLAYER_ENDPOINT
                        .sendS2C(
                            FullPlayerPacket(
                                player.id,
                                FullPlayerData.of(playerToSync)
                            ),
                            player.id
                        )
                }
            }

            state.synchronizedPlayers.removeIf { it !in playersInRadius }
        }
    }

    fun onPlayerCustomSpeedUpdate(player: Player, speed: AttributeUpdate) {
        CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT.broadcastInRadius(
            player,
            PlayerAttributeUpdatePacket(
                player.id,
                speed = speed
            )
        )
    }

    fun onPlayerJumpStrengthUpdate(player: Player, jumpStrength: AttributeUpdate) {
        CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT.broadcastInRadius(
            player,
            PlayerAttributeUpdatePacket(
                player.id,
                jumpStrength = jumpStrength
            )
        )
    }

    fun onPlayerSpeedIntention(player: Player, intention: Float) {
        CLIENTBOUND_SPEED_INTENTION_PACKET.broadcastInRadius(
            player,
            PlayerSpeedIntentionPacket(
                player.id,
                intention
            )
        )
    }

    fun onPlayerCustomName(player: Player, name: String) {
        CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT.broadcast(
            PlayerCustomNamePacket(
                player.id,
                name
            )
        )
    }

    fun onOutcomingMessage(player: Player, message: OutcomingMessage) {
        val source = message.source
        CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
            .sendS2C(
                OutcomingChatMessagePacket(
                    source.position?.let { ImmutableVec3(it) },
                    source.world.id,
                    source.author.player?.id,
                    source.author.name,
                    message.text,
                    message.channel,
                    message.mentioned,
                    message.speech,
                    message.volume,
                    message.isSpy,
                    message.placeholders
                ),
                player.id
            )
    }

    fun onServerNotification(player: Player, notification: Notification, once: Boolean) {
        CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
            .sendS2C(
                PlayerNotificationPacket(
                    notification,
                    once
                ),
                player.id
            )
    }

    fun onPlayerInstantiation(player: Player) {
        val playerId = player.id
        val world = player.world

        playerStorage.forEach {
            if (it == player) return@forEach
            CLIENTBOUND_PLAYER_JOIN_ENDPOINT.sendS2C(
                PlayerJoinServerPacket(
                    GeneralPlayerData.of(player)
                ),
                it.id
            )
        }

        coroutineScope.launch {
            CLIENTBOUND_JOIN_GAME_ENDPOINT
                .taskS2C(
                    JoinGamePacket(
                        ServerPlayerData.of(player),
                        ClientboundWorldData.of(world),
                        ClientboundSetupData.create(server, player)
                    ),
                    playerId
                )
                .send()
                .requestAcknowledge()
        }
    }

    fun onPlayerDestroy(player: Player) {
        CLIENTBOUND_PLAYER_DESTROY_ENDPOINT.broadcast(
            PlayerDestroyPacket(player.id)
        )
    }

    fun onServerSettingsUpdate() {
        CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT.broadcast {
            ServerSettingsUpdatePacket(
                ClientboundServerSettings.of(server, it)
            )
        }
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        center: Pos,
        radius: Int,
        packet: (Player) -> P
    ) {
        for (player in playerStorage) {
            if (player.pos.squaredDistanceTo(center) <= radius * radius) {
                sendS2C(packet(player), player.id)
            }
        }
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: Player,
        radius: Int = playerSynchronizationRadius,
        packet: (Player) -> P
    ) {
        broadcastInRadius(player.pos, radius, packet)
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: Player,
        packet: P,
        radius: Int = playerSynchronizationRadius,
    ) {
        broadcastInRadius(player.pos, radius, { packet })
    }
}