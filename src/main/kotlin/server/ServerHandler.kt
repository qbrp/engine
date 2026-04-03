package org.lain.engine.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lain.engine.chat.*
import org.lain.engine.container.Item
import org.lain.engine.debugPacket
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.getVoidScript
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.backupBookContent
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.packet.*
import org.lain.engine.util.component.Networked
import org.lain.engine.util.component.clearMetaState
import org.lain.engine.util.component.get
import org.lain.engine.util.component.getAll
import org.lain.engine.util.component.iterate
import org.lain.engine.util.component.require
import org.lain.engine.util.component.set
import org.lain.engine.util.injectServerTransportContext
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.*
import kotlin.let
import kotlin.math.pow

sealed class AttributeUpdate() {
    object Reset : AttributeUpdate()
    class Value(val value: Float) : AttributeUpdate()
}

enum class Notification {
    INVALID_SOURCE_POS,
    ACOUSTIC_ERROR,
    COMPILATION_ERROR,
    FREECAM,
}

class DesynchronizationException(message: String) : RuntimeException(message)

fun desync(message: String): Nothing = throw DesynchronizationException(message)

class ServerHandler(
    private val server: EngineServer,
) {
    private val transportContext by injectServerTransportContext()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val playerStorage: PlayerStorage get() = server.playerStorage
    private val itemStorage: ItemStorage get() = server.itemStorage
    private val globals: ServerGlobals get() = server.globals
    val playerSynchronizationRadius get() = globals.playerSynchronizationRadius
    private val playerDesynchronizationThreshold get() = globals.playerDesynchronizationThreshold

    private var squaredSynchronizationRadius = 0f
    private var squaredDesynchronizationRadius = 0f

    private fun updatePlayer(id: PlayerId, update: EnginePlayer.() -> Unit) {
        val player = server.playerStorage.get(id) ?: desync("Игрок не находится на сервере")
        player.update()
    }

    private fun getPlayer(id: PlayerId): EnginePlayer? {
        return server.playerStorage.get(id)
    }

    private fun execute(r: Runnable) = server.execute(r)

    fun onServerSettingsUpdate() {
        squaredSynchronizationRadius = (playerSynchronizationRadius * playerSynchronizationRadius).toFloat()
        squaredDesynchronizationRadius = (playerSynchronizationRadius + playerDesynchronizationThreshold).toFloat().pow(2)
        CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT.broadcast {
            ServerSettingsUpdatePacket(
                ClientboundServerSettings.of(server, it)
            )
        }
    }

    fun run() {
        GlobalAcknowledgeListener.start()

        SERVERBOUND_SPEED_INTENTION_PACKET.registerReceiver { ctx -> onPlayerSpeedIntentionSet(ctx.sender, value) }
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx -> onChatMessage(ctx.sender, text, channel) }
        SERVERBOUND_DEVELOPER_MODE_PACKET.registerReceiver { ctx -> onDeveloperModeEnabled(ctx.sender, status.enabled, status.acoustic) }
        SERVERBOUND_VOLUME_PACKET.registerReceiver { ctx -> onPlayerVolume(ctx.sender, volume) }
        SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.registerReceiver { ctx -> onChatMessageDelete(ctx.sender, message) }
        SERVERBOUND_CURSOR_ITEM_ENDPOINT.registerReceiver { ctx -> onPlayerCursorItem(ctx.sender, item) }
        SERVERBOUND_CHAT_TYPING_START_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingStart(ctx.sender, channel) }
        SERVERBOUND_CHAT_TYPING_END_ENDPOINT.registerReceiver { ctx -> onPlayerChatTypingEnd(ctx.sender) }
        SERVERBOUND_ARM_STATUS_ENDPOINT.registerReceiver { ctx -> onPlayerArmStatus(ctx.sender, extend) }
        SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT.registerReceiver { ctx -> onWriteableContentsUpdate(ctx.sender, item, contents) }
        SERVERBOUND_INPUT_PACKET.registerReceiver { ctx -> onPlayerInput(ctx.sender, tick + 2, actions) }
        SERVERBOUND_INTERACTION_SELECTION_SELECT_ENDPOINT.registerReceiver { ctx -> onInteractionSelectionSelect(ctx.sender, variantId) }
        SERVERBOUND_CLIENT_TICK_END_ENDPOINT.registerReceiver { ctx ->
            val player = getPlayer(ctx.sender) ?: return@registerReceiver
            player.network.tick++
        }
        SERVERBOUND_VOXEL_BLOCK_HINT_PACKET.registerReceiver { ctx -> onVoxelBlockHint(ctx.sender, pos, action) }
        SERVERBOUND_SCRIPT_BINDINGS_ENDPOINT.registerReceiver { ctx -> onScriptBindings(ctx.sender, bindings) }
    }

    fun invalidate() {
        transportContext.unregisterAll()
    }

    private fun onScriptBindings(player: PlayerId, bindings: ScriptBindings) = updatePlayer(player) {
        fun <C : ScriptContext> ScriptId.ensureExists() = require(server.namespacedStorage.getVoidScript<C>(this) != null)
        bindings.base?.ensureExists<ScriptContext.Player>()
        bindings.attack?.ensureExists<ScriptContext.Player>()
        set(bindings)
    }

    private fun onVoxelBlockHint(player: PlayerId, pos: VoxelPos, action: VoxelBlockHintPacket.Action) = updatePlayer(player) {
        when (action) {
            is VoxelBlockHintPacket.Action.Add -> {
                if (hasPermission("blockhint.set")) {
                    world.singleBlockVoxelEvent(pos, VoxelUpdate.AddHint(action.text))
                }
            }
            is VoxelBlockHintPacket.Action.Remove -> {
                if (hasPermission("blockhint.remove")) {
                    world.singleBlockVoxelEvent(pos, VoxelUpdate.RemoveHint(action.index))
                }
            }
        }
    }

    private fun onInteractionSelectionSelect(playerId: PlayerId, variantId: String?) = updatePlayer(playerId) {
        val interaction = get<InteractionComponent>() ?: desync("Взаимодействие не выполняется")
        val variant = variantId?.let { interaction.selection?.variants?.firstOrNull { it.id == variantId } ?: desync("Вариант взаимодействия $variantId не существует") }
        interaction.selection = null
        interaction.selectionVariant = variant
        if (variant == null) {
            interaction.selectionCancelled = true
        }
        CLIENTBOUND_PLAYER_INTERACTION_SELECTION_SELECT_ENDPOINT.broadcastInRadius(
            this,
            PlayerInteractionSelectionSelectPacket(playerId, variant?.id),
            true,
            playerSynchronizationRadius,
        )
    }

    private fun onPlayerInput(playerId: PlayerId, tick: Long, input: Set<InputActionDto>) = updatePlayer(playerId) {
        val playerInput = this.require<PlayerInput>()
        val actions = input.map { it.toDomain(itemStorage) }
        playerInput.actions.clear()
        playerInput.actions.addAll(actions)

        CLIENTBOUND_PLAYER_INPUT_PACKET.broadcastInRadius(
            location,
            playerSynchronizationRadius,
            exclude = listOf(this),
            packet = PlayerInputPacket(this.id, actions.map { action -> action.toDto() }.toSet())
        )
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
            if (!hasPermission("invsee") && result.owner != null && result.owner?.id != playerId) {
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
            val world = player.world
            val input = player.require<PlayerInput>()
            val state = player.network
            val location = player.location

            debugPacket("Действия тика ${state.tick}: ${input.actions}")

            val playersToSynchronize = filterNearestPlayers(location, playerSynchronizationRadius, players).toMutableList()
            val playersToDesynchronize = state.players.filter { it.pos.squaredDistanceTo(player.pos) > squaredDesynchronizationRadius }
            state.players.removeAll(playersToDesynchronize)

            val items: HashSet<ItemUuid> = hashSetOf()
            world.iterate<Item, HoldsBy> { entity, (engineItem), (owner) ->
                items.add(engineItem.uuid)
                if (engineItem.uuid !in state.items) {
                    state.items += engineItem.uuid
                    val packet = ItemPacket(ClientboundItemData.from(world, engineItem))
                    val task = CLIENTBOUND_ITEM_ENDPOINT.taskS2C(
                        packet,
                        player.id
                    )
                        .send()
                        .withAcknowledge()
                        .onTimeoutServerThread { state.items -= engineItem.uuid }
                    coroutineScope.launch { task.run() }
                }
            }

            state.items.removeIf { it !in items }

            world.iterate<Networked, Location, PersistentId>() { entity, _, location, persistentId ->
                if (location.position.squaredDistanceTo(location.position) < squaredSynchronizationRadius) {
                    val delta = world.componentManager.getDeltaBitMask(entity)
                    val componentsToSynchronize = entity.getAll(delta)
                    if (componentsToSynchronize.isNotEmpty()) {
                        CLIENTBOUND_ENTITY_ENDPOINT.sendS2C(
                            EntityPacket(persistentId, componentsToSynchronize),
                            player.id
                        )
                    }
                }
            }

            tickSynchronizationComponent(playerStorage, player)
            player.items.forEach { item ->
                val component = item.get<Synchronizations<EngineItem>>()
                if (component != null) {
                    tickSynchronizationComponent(playerStorage, item, component)
                }
            }

            for (playerToSynchronize in playersToSynchronize) {
                if (playerToSynchronize !in state.players && playerToSynchronize.id != player.id) {
                    state.players += playerToSynchronize
                    val task = CLIENTBOUND_FULL_PLAYER_ENDPOINT
                            .taskS2C(
                                FullPlayerPacket(
                                    playerToSynchronize.id,
                                    FullPlayerData.of(playerToSynchronize)
                                ),
                                player.id
                            )
                            .send()
                            .withAcknowledge()
                            .onTimeoutServerThread { state.players -= playerToSynchronize }
                    coroutineScope.launch { task.run() }
                }
            }
        }

        server.listWorlds().forEach {
            it.iterate<Networked>() { entity, _ -> entity.clearMetaState() }
        }
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

    fun onOutcomingMessage(player: MessageSource.Player, message: OutcomingMessage) {
        val source = message.source
        CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
            .sendS2C(
                OutcomingChatMessagePacket(message),
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

    fun onServerNotification(player: PlayerId, notification: Notification, once: Boolean) {
        server.playerStorage.get(player)?.let { onServerNotification(it, notification, once) }
    }

    fun onPlayerInstantiation(player: EnginePlayer, notifications: List<Notification> = listOf()) {
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

        val synchronization = player.network
        val joinGamePacket = JoinGamePacket(
            ServerPlayerData.of(player),
            ClientboundWorldData.of(world),
            ClientboundSetupData.create(server, player),
            notifications
        )
        val task = CLIENTBOUND_JOIN_GAME_ENDPOINT
            .taskS2C(joinGamePacket, playerId)
            .send()
            .withAcknowledge()
            .onTimeoutServerThread { synchronization.disconnect = true }
        coroutineScope.launch {
            task.run()
            server.execute {
                synchronization.authorized = true
                synchronization.items += joinGamePacket.playerData.referencedItems.all
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

    fun onPersonalVolumeAcousticDebug(player: EnginePlayer, volumes: List<Pair<ImmutableVoxelPos, Float>>) {
        CLIENTBOUND_ACOUSTIC_DEBUG_VOLUMES_PACKET.sendS2C(
            AcousticDebugVolumesPacket(volumes),
            player.id
        )
    }

    fun onVoxelEvent(world: World, event: VoxelEvent, players: Collection<EnginePlayer>) {
        players.forEach {
            if (it.world != world) return@forEach
            CLIENTBOUND_VOXEL_EVENT_PACKET.sendS2C(
                VoxelEventPacket(event),
                it.id
            )
        }
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

    fun <P : Packet> Endpoint<P>.broadcastInRadiusFor(
        center: Location,
        radius: Int,
        players: List<EnginePlayer>,
        packet: P
    ) {
        for (player in players) {
            if (player.world == center.world && player.pos.squaredDistanceTo(center.position) <= radius * radius) {
                sendS2C(packet, player.id)
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