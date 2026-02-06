package org.lain.engine.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.lain.engine.chat.CHAT_LOGGER
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.item.Gun
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.SoundEvent
import org.lain.engine.item.SoundPlay
import org.lain.engine.player.CustomName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Interaction
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.acousticDebug
import org.lain.engine.player.developerMode
import org.lain.engine.player.intentSpeed
import org.lain.engine.player.items
import org.lain.engine.player.speak
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.ServerTransportContext
import org.lain.engine.transport.packet.AcousticDebugVolumesPacket
import org.lain.engine.transport.packet.CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET
import org.lain.engine.transport.packet.GlobalAcknowledgeListener
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_FULL_PLAYER_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_ITEM_GUN_PACKET
import org.lain.engine.transport.packet.CLIENTBOUND_ITEM_PACKET
import org.lain.engine.transport.packet.CLIENTBOUND_JOIN_GAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_DESTROY_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_JOIN_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SOUND_PLAY_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.ClientboundItemData
import org.lain.engine.transport.packet.ClientboundServerSettings
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.ContentsUpdatePacket
import org.lain.engine.transport.packet.DeleteChatMessagePacket
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.transport.packet.JoinGamePacket
import org.lain.engine.transport.packet.OutcomingChatMessagePacket
import org.lain.engine.transport.packet.PlayerAttributeUpdatePacket
import org.lain.engine.transport.packet.PlayerCustomNamePacket
import org.lain.engine.transport.packet.PlayerNotificationPacket
import org.lain.engine.transport.packet.FullPlayerPacket
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ItemGunPacket
import org.lain.engine.transport.packet.ItemPacket
import org.lain.engine.transport.packet.PlayerDestroyPacket
import org.lain.engine.transport.packet.PlayerJoinServerPacket
import org.lain.engine.transport.packet.PlayerSpeedIntentionPacket
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_INTERACTION_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_PLAYER_CURSOR_ITEM_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.transport.packet.ServerSettingsUpdatePacket
import org.lain.engine.transport.packet.ServerboundInteractionData
import org.lain.engine.transport.packet.SoundPlayPacket
import org.lain.engine.util.ImmutableVec3
import org.lain.engine.util.Pos
import org.lain.engine.util.filterNearestPlayers
import org.lain.engine.util.require
import org.lain.engine.util.set
import org.lain.engine.world.ImmutableVoxelPos
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.location
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
    private val server: EngineServer,
    private val transport: ServerTransportContext
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playerStorage: PlayerStorage
        get() = server.playerStorage
    private val itemStorage: ItemStorage
        get() = server.itemStorage
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

    private fun updatePlayer(id: PlayerId, update: EnginePlayer.() -> Unit) = execute {
        server.playerStorage.get(id)?.update()
    }

    private fun getPlayer(id: PlayerId): EnginePlayer? {
        return server.playerStorage.get(id)
    }

    private fun execute(r: Runnable) = server.execute(r)

    fun run() {
        GlobalAcknowledgeListener.start()

        SERVERBOUND_SPEED_INTENTION_PACKET.registerReceiver { ctx -> onPlayerSpeedIntentionSet(ctx.sender, value) }
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx -> onChatMessage(ctx.sender, text, channel) }
        SERVERBOUND_DEVELOPER_MODE_PACKET.registerReceiver { ctx -> onDeveloperModeEnabled(ctx.sender, enabled, acoustic) }
        SERVERBOUND_VOLUME_PACKET.registerReceiver { ctx -> onPlayerVolume(ctx.sender, volume) }
        SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx -> onChatMessageDelete(ctx.sender, message) }
        SERVERBOUND_INTERACTION_ENDPOINT.registerReceiver { ctx -> onPlayerInteraction(ctx.sender, interaction) }
        SERVERBOUND_PLAYER_CURSOR_ITEM_ENDPOINT.registerReceiver { ctx -> onPlayerCursorItem(ctx.sender, item) }
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

    private fun onDeveloperModeEnabled(playerId: PlayerId, enabled: Boolean, acoustic: Boolean) = updatePlayer(playerId) {
        developerMode = enabled
        acousticDebug = acoustic
    }

    private fun onPlayerInteraction(playerId: PlayerId, interaction: ServerboundInteractionData) = updatePlayer(playerId) {
        val interaction = try {
            interaction.toDomain(itemStorage)
        } catch (e: Throwable) {
            return@updatePlayer
        }
        set(InteractionComponent(interaction))
    }

    private fun onPlayerCursorItem(playerId: PlayerId, itemId: ItemUuid?) = updatePlayer(playerId) {
        // FIXME: Искать предметы по инвентарю игрока, а не глобально
        require<PlayerInventory>().cursorItem = itemId?.let {
            itemStorage.get(it) ?: error("Item $itemId not found")
        }
    }

    private fun onChatMessageDelete(by: PlayerId, messageId: MessageId) {
        val player = playerStorage.get(by) ?: return
        val chat = server.chat
        val outcomingMessage = chat.outcomingMessageHistory[messageId] ?: return
        if (outcomingMessage.source.author.player?.id != player.id) return
        val packet = DeleteChatMessagePacket(messageId)
        playerStorage.getAll().forEach {
            if (it.id == player.id) return@forEach
            CLIENTBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.sendS2C(packet, it.id)
        }
        CHAT_LOGGER.info("Удалено сообщение игроком $player: $outcomingMessage")
    }

    fun synchronizePlayers() {
        val players = playerStorage.filter { it.synchronization.authorized }
        for (player in players) {
            val state = player.synchronization
            val location = player.location
            val playersInRadius = filterNearestPlayers(location, globals.playerSynchronizationRadius, players)
            val playersToSync = playersInRadius
                .filter { it !in state.synchronizedPlayers }
            for (playerToSync in playersToSync) {
                if (playerToSync.id != player.id) {
                    state.synchronizedPlayers += playerToSync

                    // Игрок будет кикнут, если состояние синхронизации не придёт
                    coroutineScope.launch {
                        CLIENTBOUND_FULL_PLAYER_ENDPOINT
                            .sendS2C(
                                FullPlayerPacket(
                                    playerToSync.id,
                                    FullPlayerData.of(playerToSync)
                                ),
                                player.id
                            )
                    }
                }

                coroutineScope.launch {
                    val awaits = mutableListOf<Job>()
                    for (item in playerToSync.items) {
                        if (item.uuid !in state.synchronizedItems) {
                            awaits += coroutineScope.launch {
                                CLIENTBOUND_ITEM_PACKET.taskS2C(
                                    ItemPacket(ClientboundItemData.from(item)),
                                    player.id
                                )
                                    .send()
                                    .requestAcknowledge()
                            }
                            state.synchronizedItems += item.uuid
                        }
                    }
                    awaits.joinAll()
                }
            }

            state.synchronizedPlayers.removeIf { it !in playersInRadius }
        }
    }

    fun onContentsUpdate() {
        CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT.broadcast(ContentsUpdatePacket)
    }

    fun onSoundEvent(play: SoundPlay, receivers: List<EnginePlayer>) {
        val packet = SoundPlayPacket(play)
        receivers.forEach {
            CLIENTBOUND_SOUND_PLAY_ENDPOINT.sendS2C(packet, it.id)
        }
    }

    fun onPlayerCustomSpeedUpdate(player: EnginePlayer, speed: AttributeUpdate) {
        CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT.broadcastInRadius(
            player,
            PlayerAttributeUpdatePacket(
                player.id,
                speed = speed
            )
        )
    }

    fun onPlayerJumpStrengthUpdate(player: EnginePlayer, jumpStrength: AttributeUpdate) {
        CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT.broadcastInRadius(
            player,
            PlayerAttributeUpdatePacket(
                player.id,
                jumpStrength = jumpStrength
            )
        )
    }

    fun onPlayerSpeedIntention(player: EnginePlayer, intention: Float) {
        CLIENTBOUND_SPEED_INTENTION_PACKET.broadcastInRadius(
            player,
            PlayerSpeedIntentionPacket(
                player.id,
                intention
            )
        )
    }

    fun onPlayerCustomName(player: EnginePlayer, name: CustomName?) {
        CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT.broadcast(
            PlayerCustomNamePacket(
                player.id,
                name
            )
        )
    }

    fun onItemGunUpdate(
        player: EnginePlayer,
        item: ItemUuid,
        selector: Boolean? = null,
        barrelBullets: Int? = null
    ) {
        CLIENTBOUND_ITEM_GUN_PACKET.broadcastInRadius(
            player,
            ItemGunPacket(item, selector, barrelBullets)
        )
    }

    fun onOutcomingMessage(player: EnginePlayer, message: OutcomingMessage) {
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
                    message.placeholders,
                    message.head,
                    message.notify,
                    message.id
                ),
                player.id
            )
    }

    fun onServerNotification(player: EnginePlayer, notification: Notification, once: Boolean) {
        CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
            .sendS2C(
                PlayerNotificationPacket(
                    notification,
                    once
                ),
                player.id
            )
    }

    fun onPlayerInstantiation(player: EnginePlayer) {
        val playerId = player.id
        val world = player.world
        val packet = PlayerJoinServerPacket(GeneralPlayerData.of(player))

        playerStorage.forEach {
            if (it == player) return@forEach
            CLIENTBOUND_PLAYER_JOIN_ENDPOINT.sendS2C(
                packet,
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
//                .requestAcknowledge()
            server.execute { player.synchronization.authorized = true }
        }
    }

    fun onPlayerDestroy(player: EnginePlayer) {
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

    fun onPersonalVolumeAcousticDebug(player: EnginePlayer, volumes: List<Pair<ImmutableVoxelPos, Float>>) {
        CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET.sendS2C(
            AcousticDebugVolumesPacket(volumes),
            player.id
        )
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        center: Pos,
        radius: Int,
        packet: (EnginePlayer) -> P
    ) {
        for (player in playerStorage) {
            if (player.pos.squaredDistanceTo(center) <= radius * radius) {
                sendS2C(packet(player), player.id)
            }
        }
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        radius: Int = playerSynchronizationRadius,
        packet: (EnginePlayer) -> P
    ) {
        broadcastInRadius(player.pos, radius, packet)
    }

    private fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        packet: P,
        radius: Int = playerSynchronizationRadius,
    ) {
        broadcastInRadius(player.pos, radius, { packet })
    }
}