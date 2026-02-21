package org.lain.engine.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lain.engine.chat.*
import org.lain.engine.debugPacket
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.storage.backupBookContent
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.packet.*
import org.lain.engine.util.file.loadContents
import org.lain.engine.util.get
import org.lain.engine.util.injectServerTransportContext
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.util.require
import org.lain.engine.world.*
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

class DesynchronizationException(message: String) : RuntimeException(message)

fun desync(message: String): Nothing = throw DesynchronizationException(message)

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
        val player = server.playerStorage.get(id) ?: desync("Игрок не находится на сервере")
        player.update()
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
        SERVERBOUND_CURSOR_ITEM_ENDPOINT.registerReceiver { ctx -> onPlayerCursorItem(ctx.sender, item) }
        SERVERBOUND_CHAT_TYPING_START_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingStart(ctx.sender, channel) }
        SERVERBOUND_CHAT_TYPING_END_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingEnd(ctx.sender) }
        SERVERBOUND_ARM_STATUS_ENDPOINT.registerReceiver { ctx -> onPlayerArmStatus(ctx.sender, extend) }
        SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT.registerReceiver { ctx -> onWriteableContentsUpdate(ctx.sender, item, contents) }
        SERVERBOUND_INPUT_PACKET.registerReceiver { ctx -> onPlayerInput(ctx.sender, tick + 2, actions) }
        SERVERBOUND_CLIENT_TICK_END_ENDPOINT.registerReceiver { ctx ->
            val player = getPlayer(ctx.sender) ?: return@registerReceiver
            player.network.tick++
        }
        SERVERBOUND_RELOAD_CONTENTS_REQUEST_ENDPOINT.registerReceiver { ctx -> onRequestReloadContents(ctx.sender) }
    }

    fun invalidate() {
        transportContext.unregisterAll()
        destroy = true
    }

    fun onRequestReloadContents(playerId: PlayerId) = updatePlayer(playerId) {
        if (hasPermission("reloadenginecontents")) {
            server.loadContents()
        }
        CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT.sendS2C(ContentsUpdatePacket, playerId)
    }

    fun onPlayerInteraction(player: EnginePlayer, component: InteractionComponent) {
        CLIENTBOUND_PLAYER_INTERACTION_PACKET.broadcastInRadius(
            player.location,
            playerSynchronizationRadius,
            packet = PlayerInteractionPacket(
                player.id,
                component.toDto()
            )
        )
    }

    private fun onPlayerInput(playerId: PlayerId, tick: Long, input: Set<InputActionDto>) = updatePlayer(playerId) {
        //println("Выполнено действие $input тика $tick")
        val actions = input.map { it.toDomain(itemStorage) }
        val network = network
        network.actionTasks[tick] = actions
    }

    private fun onWriteableContentsUpdate(playerId: PlayerId, itemUuid: ItemUuid, contents: List<String>) = updatePlayer(playerId) {
        val item = this.handItem
        val writable = this.handItem?.get<Writable>()
        if (item?.uuid != itemUuid || writable == null) desync("Предмет для сохранения написанного контента не найден или им не является")
        if (contents.count() > writable.pages) desync("Страниц написано больше, чем возможно")

        if (writable.contents != contents) {
            writable.contents = contents
            item.markDirty<Writable>()
            backupBookContent(username, item.id, contents)
        }
    }

    private fun onPlayerArmStatus(playerId: PlayerId, extend: Boolean) = updatePlayer(playerId) {
        extendArm = extend
        markDirty<ArmStatus>()
    }

    private val typingPlayers = mutableSetOf<PlayerId>()

    private fun onPlayerChatTypingStart(player: PlayerId, channelId: ChannelId) = updatePlayer(player) {
        val player = this
        val channel = server.chat.getChannel(channelId)
        val acoustic = channel.acoustic

        if (!channel.typeIndicator || acoustic == null) {
            return@updatePlayer
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
        val settings = require<DefaultPlayerAttributes>()
        if (volume > settings.maxVolume || volume < 0) {
            desync("Недопустимый уровень громкости")
        }
        require<VoiceApparatus>().inputVolume = volume
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

    // FIXME: Искать предметы по инвентарю игрока, а не глобально
    private fun onPlayerCursorItem(playerId: PlayerId, itemId: ItemUuid?) = updatePlayer(playerId) {
        val item = itemId?.let {
            val result = itemStorage.get(it) ?: desync("Установленный курсором предмет $itemId не найден")
            if (result.owner != null && result.owner != playerId) {
                desync("Захвачен чужой предмет")
            }
            result
        }
        require<PlayerInventory>().cursorItem = item
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
        val players = playerStorage
            .filter { it.network.authorized }

        for (player in players) {
            val input = player.require<PlayerInput>()
            val state = player.network
            val location = player.location
            val actionTasks = state.actionTasks

            val actionTask = actionTasks.remove(state.tick) ?: run {
                val last = state.lastActions.toList()
                for (actions in last.reversed()) {
                    if (actions.isNotEmpty()) {
                        return@run actions
                    }
                }
                null
            }

            if (actionTask != null) {
                input.actions.clear()
                input.actions.addAll(actionTask)

                CLIENTBOUND_PLAYER_INPUT_PACKET.broadcastInRadius(
                    location,
                    playerSynchronizationRadius,
                    exclude = listOf(player),
                    packet = PlayerInputPacket(player.id, actionTask.map { action -> action.toDto() }.toSet())
                )

                state.lastActions.add(actionTask)
            }

            debugPacket("Действия тика ${state.tick}: $actionTask")

            val toRemove = mutableListOf<Long>()
            if (actionTasks.size > 100) {
                for ((tick, _) in actionTasks) {
                    if (tick < state.tick) {
                        toRemove.add(tick)
                    }
                }
            }
            toRemove.forEach { actionTasks.remove(it) }

            val playersInRadius = filterNearestPlayers(location, globals.playerSynchronizationRadius, players)

            tickSynchronizationComponent(playerStorage, player)
            player.items.forEach { item ->
                val component = item.get<Synchronizations<EngineItem>>()
                if (component != null) {
                    tickSynchronizationComponent(playerStorage, item, component)
                }
            }

            val items: MutableList<ItemUuid> = mutableListOf()
            for (playerInRadius in playersInRadius) {
                if (playerInRadius !in state.players && playerInRadius.id != player.id) {
                    state.players += playerInRadius

                    // Игрок будет кикнут, если состояние синхронизации не придёт
                    coroutineScope.launch {
                        CLIENTBOUND_FULL_PLAYER_ENDPOINT
                            .taskS2C(
                                FullPlayerPacket(
                                    playerInRadius.id,
                                    FullPlayerData.of(playerInRadius)
                                ),
                                player.id
                            )
                            .send()
                            .withAcknowledge()
                            .onTimeoutServerThread { state.players -= playerInRadius }
                            .run()
                    }
                }

                for (item in playerInRadius.items) {
                    items.add(item.uuid)
                    if (item.uuid !in state.items) {
                        state.items += item.uuid
                        val packet = ItemPacket(ClientboundItemData.from(item))
                        coroutineScope.launch {
                            CLIENTBOUND_ITEM_ENDPOINT.taskS2C(
                                packet,
                                player.id
                            )
                                .send()
                                .withAcknowledge()
                                .onTimeoutServerThread { state.items -= item.uuid }
                                .run()
                        }
                    }
                }
            }

            state.players.removeIf { it !in playersInRadius }
            state.items.removeIf { it !in items }
        }
    }

    fun onChunkSend(chunk: EngineChunk, pos: EngineChunkPos, player: EnginePlayer) {
        CLIENTBOUND_CHUNK_ENDPOINT.sendS2C(
            EngineChunkPacket(
                pos,
                chunk.decals.mapKeys { (k, v) -> ImmutableVoxelPos(k) },
                chunk.hints.mapKeys { (k, v) -> ImmutableVoxelPos(k) }
            ),
            player.id
        )
        player.network.chunks += pos
    }

    fun onContentsUpdate() {
        CLIENTBOUND_CONTENTS_UPDATE_ENDPOINT.broadcast(ContentsUpdatePacket)
    }

    fun onSoundEvent(play: SoundPlay, context: SoundContext?, receivers: List<EnginePlayer>) {
        val packet = SoundPlayPacket(play, context)
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
            val synchronization = player.network
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
                .withAcknowledge()
                .onTimeoutServerThread { synchronization.disconnect = true }
                .run()
            server.execute {
                synchronization.authorized = true
            }
        }
    }

    fun onPlayerDestroy(player: EnginePlayer) {
        CLIENTBOUND_PLAYER_DESTROY_ENDPOINT.broadcast(
            PlayerDestroyPacket(player.id)
        )
        playerStorage.forEach { it.network.players.remove(player) }
    }

    fun onItemsBatchDestroy(item: List<ItemUuid>) {
        playerStorage.forEach { it.network.items.removeAll(item) }
    }

    fun onChunkUnload(chunk: EngineChunkPos) {
        playerStorage.forEach { it.network.chunks.remove(chunk) }
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

    fun onVoxelDestroy(world: World, voxelPos: ImmutableVoxelPos) {
//        val packet = DestroyVoxelPacket(voxelPos)
//        CLIENTBOUND_VOXEL_DESTROY_ENDPOINT.broadcastOutSimulationRadius(world, voxelPos) { packet }
    }

    fun onVoxelDecalsUpdate(world: World, voxelPos: VoxelPos) {
        val packet = VoxelUpdatePacket(
            ImmutableVoxelPos(voxelPos),
            world.chunkStorage.getDecals(voxelPos),
            null
        )
        CLIENTBOUND_VOXEL_UPDATE_ENDPOINT.broadcastOutSimulationRadius(world, voxelPos) { packet }
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

    fun <P : Packet> Endpoint<P>.broadcastOutSimulationRadius(
        world: World,
        pos: VoxelPos,
        packet: (EnginePlayer) -> P
    ) {
        for (player in playerStorage) {
            if (player.world == world && player.pos.squaredDistanceTo(pos) >= playerSynchronizationRadius * playerSynchronizationRadius) {
                sendS2C(packet(player), player.id)
            }
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        center: Location,
        radius: Int,
        exclude: List<EnginePlayer> = emptyList(),
        packet: P
    ) {
        for (player in playerStorage) {
            if (player !in exclude && player.world == center.world && player.pos.squaredDistanceTo(center.position) <= radius * radius) {
                sendS2C(packet, player.id)
            }
        }
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        radius: Int = playerSynchronizationRadius,
        packet: P
    ) {
        broadcastInRadius(player.location, radius, packet=packet)
    }

    fun <P : Packet> Endpoint<P>.broadcastInRadius(
        player: EnginePlayer,
        packet: P,
        excludeSelf: Boolean = false,
        radius: Int = playerSynchronizationRadius
    ) {
        broadcastInRadius(player.location, radius, exclude=if (excludeSelf) listOf(player) else emptyList(), packet=packet)
    }
}