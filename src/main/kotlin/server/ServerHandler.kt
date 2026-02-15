package org.lain.engine.server

import kotlinx.coroutines.*
import org.lain.engine.chat.*
import org.lain.engine.item.ItemStorage
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.SoundPlay
import org.lain.engine.player.*
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.packet.*
import org.lain.engine.util.getOrSet
import org.lain.engine.util.injectServerTransportContext
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.util.require
import org.lain.engine.world.*
import java.lang.Runnable
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

sealed class AttributeUpdate() {
    object Reset : AttributeUpdate()
    class Value(val value: Float) : AttributeUpdate()
}

enum class Notification {
    INVALID_SOURCE_POS,
    VOICE_BREAK,
    VOICE_TIREDNESS,
    FREECAM,
}

class ServerHandler(
    private val server: EngineServer,
) {
    private val transportContext by injectServerTransportContext()
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

    private fun updatePlayer(id: PlayerId, update: EnginePlayer.() -> Unit) {
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
        SERVERBOUND_CHAT_TYPING_START_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingStart(ctx.sender, channel) }
        SERVERBOUND_CHAT_TYPING_END_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingEnd(ctx.sender) }
        SERVERBOUND_PLAYER_ARM_ENDPOINT.registerReceiver { ctx -> onPlayerArmStatus(ctx.sender, extend) }
    }

    fun invalidate() {
        transportContext.unregisterAll()
        destroy = true
    }

    private fun onPlayerArmStatus(playerId: PlayerId, extend: Boolean) = updatePlayer(playerId) {
        extendArm = extend
        markDirty<ArmStatus>()
    }

    private val typingPlayers = mutableSetOf<PlayerId>()

    private fun onPlayerChatTypingStart(player: PlayerId, channelId: ChannelId) {
        val player = server.playerStorage.get(player) ?: error("Player $player not found")
        val channel = server.chat.getChannel(channelId)
        val acoustic = channel.acoustic

        if (!channel.typeIndicator || acoustic == null) {
            return
        }

        typingPlayers.add(player.id)

        val range = channel.typeIndicatorRange
        val nearestPlayers = range?.let { player.filterNearestPlayers(it) }
        val players = nearestPlayers ?: when(acoustic) {
            is Acoustic.Global -> playerStorage.getAll()
            is Acoustic.Distance -> player.filterNearestPlayers(acoustic.radius)
            is Acoustic.Realistic -> {
                val radius = server.chat.settings.defaultChannel.typeIndicatorRange ?: 16
                CHAT_LOGGER.warn("Акустическая симуляция не работает, чтобы подсчитать, каким игрокам отображать индикатор ввода сообщения. Используется стандартный радиус $radius блоков.")
                player.filterNearestPlayers(radius)
            }
        }.filter { it.isChannelAvailableToRead(channel) }
        val packet = ChatTypingPlayerPacket(player.id)
        players.forEach { CLIENTBOUND_CHAT_TYPING_PLAYER_START_ENDPOINT.sendS2C(packet, it.id) }
    }

    private fun onPlayerChatTypingEnd(player: PlayerId) {
        CLIENTBOUND_CHAT_TYPING_PLAYER_END_ENDPOINT.broadcast(ChatTypingPlayerPacket(player))
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

    private fun onPlayerInteraction(playerId: PlayerId, interaction: PacketInteractionData) = updatePlayer(playerId) {
        val interaction = interaction.toDomain(itemStorage) ?: return@updatePlayer
        getOrSet { InteractionComponent(interaction) }
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

    fun tick() {
        val players = playerStorage.filter { it.synchronization.authorized }
        for (player in players) {
            val state = player.synchronization
            val location = player.location
            val playersInRadius = filterNearestPlayers(location, globals.playerSynchronizationRadius, players)
            val playersToSync = playersInRadius
                .filter { it !in state.synchronizedPlayers }

            tickSynchronizationComponent(player)

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
                                CLIENTBOUND_ITEM_ENDPOINT.taskS2C(
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

    fun onPlayerInteraction(player: EnginePlayer, interaction: Interaction) {
        CLIENTBOUND_PLAYER_INTERACTION_PACKET.broadcastExcluding(
            exclude = listOf(player),
            PlayerInteractionPacket(
                player.id,
                PacketInteractionData.from(interaction)
            )
        )
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
            ),
            excludeSelf = true
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
                    message.volumes,
                    message.isSpy,
                    message.placeholders,
                    message.head,
                    message.notify,
                    message.color,
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
                .requestAcknowledge()
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

    fun <P : Packet> Endpoint<P>.broadcastExcluding(
        exclude: List<EnginePlayer> = emptyList(),
        packet: P
    ) {
        for (player in playerStorage) {
            if (player !in exclude) {
                sendS2C(packet, player.id)
            }
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        center: Location,
        radius: Int,
        exclude: List<EnginePlayer> = emptyList(),
        packet: (EnginePlayer) -> P
    ) {
        for (player in playerStorage) {
            if (player !in exclude && player.world == center.world && player.pos.squaredDistanceTo(center.position) <= radius * radius) {
                sendS2C(packet(player), player.id)
            }
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        radius: Int = playerSynchronizationRadius,
        packet: (EnginePlayer) -> P
    ) {
        broadcastInRadius(player.location, radius, packet=packet)
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        packet: P,
        excludeSelf: Boolean = false,
        radius: Int = playerSynchronizationRadius
    ) {
        broadcastInRadius(player.location, radius, exclude=if (excludeSelf) listOf(player) else emptyList(), packet={ packet })
    }
}