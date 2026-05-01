package org.lain.engine.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.lain.cyberia.ecs.clearMetaState
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.getAll
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.require
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.set
import org.lain.engine.chat.*
import org.lain.engine.debugPacket
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.getVoidScript
import org.lain.engine.storage.EntityDto
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.backupBookContent
import org.lain.engine.storage.toCommonDto
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.transport.packet.*
import org.lain.engine.util.Intent
import org.lain.engine.util.component.Networked
import org.lain.engine.util.forEachWithContext
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

    private fun updatePlayerWithContext(id: PlayerId, update: context(World) EnginePlayer.() -> Unit) {
        val player = server.playerStorage.get(id) ?: desync("Игрок не находится на сервере")
        with(player.world) { player.update() }
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
        SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.registerReceiver { ctx -> onPlayerInstantiationConfirm(ctx.sender) }
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

    private fun onPlayerInput(playerId: PlayerId, tick: Long, input: Set<InputActionDto>) = updatePlayerWithContext(playerId) {
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

    private fun onWriteableContentsUpdate(playerId: PlayerId, PersistentId: PersistentId, contents: List<String>) = updatePlayerWithContext(playerId) {
        val item = this.handItem
        val writable = this.handItem?.getComponent<Writable>()
        if (item?.requireComponent<PersistentId>() != PersistentId || writable == null) desync("Предмет для сохранения написанного контента не найден или им не является")
        if (contents.count() > writable.pages) desync("Страниц написано больше, чем возможно")

        if (writable.contents != contents) {
            writable.contents = contents
            item.markDirty<Writable>()
            backupBookContent(username, item.requireComponent<ItemMeta>().id, contents)
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
    private fun onPlayerCursorItem(playerId: PlayerId, itemId: PersistentId?) = updatePlayerWithContext(playerId) {
        val item = itemId?.let {
            val result = itemStorage.get(it.value) ?: desync("Установленный курсором предмет $itemId не найден")
            val owner = result.getOwner()
            if (!hasPermission("invsee") && owner != null && owner.id != playerId) {
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

        players.forEachWithContext({ it.world }) { player ->
            val world = player.world
            val input = player.require<PlayerInput>()
            val state = player.network
            val playerLocation = player.location
            val playerPosition = playerLocation.position

            debugPacket("Действия тика ${state.tick}: ${input.actions}")

            val playersToSynchronize = filterNearestPlayers(playerLocation, playerSynchronizationRadius, players).toMutableList()
            val playersToDesynchronize = state.players.filter { it.pos.squaredDistanceTo(playerPosition) > squaredDesynchronizationRadius }
            state.players.removeAll(playersToDesynchronize)

            val items: HashSet<PersistentId> = hashSetOf()
            world.iterate<Networked, Location, PersistentId>() { entity, _, entityLocation, persistentId ->
                if (entity.hasComponent<Player>()) return@iterate
                if (entityLocation.position.squaredDistanceTo(playerPosition) < squaredSynchronizationRadius) {
                    val delta = world.componentManager.getOrCreateNetworkedDeltaBitMask(entity)
                    val componentsToSynchronize = entity.getAll(delta)
                    if (componentsToSynchronize.isNotEmpty()) {
                        CLIENTBOUND_ENTITY_DELTA_ENDPOINT.sendS2C(
                            EntityDeltaPacket(
                                EntityDto(
                                    persistentId,
                                    componentsToSynchronize.map { it.toCommonDto() }
                                )
                            ),
                            player.id
                        )
                    }
                    if (entity.hasComponent<Item>() && persistentId !in items) {
                        items.add(persistentId)
                    }

                }
            }
            state.items.removeIf { it !in items }

            world.iterate<Networked, DynamicVoxel, ChunkedPos> { voxel, _, (voxelPos), (chunkPos, _, centerPos) ->
                if (centerPos.squaredDistanceTo(playerPosition) < squaredSynchronizationRadius) {
                    val delta = world.componentManager.getOrCreateNetworkedDeltaBitMask(voxel)
                    val componentsToSynchronize = voxel.getAll(delta)
                    if (componentsToSynchronize.isNotEmpty()) {
                        CLIENTBOUND_DYNAMIC_VOXEL_DELTA_ENDPOINT.sendS2C(
                            DynamicVoxelDeltaPacket(
                                ImmutableVoxelPos(voxelPos),
                                componentsToSynchronize.map { it.toCommonDto() }
                            ),
                            player.id
                        )
                    }
                }
            }

            tickSynchronizationComponent(playerStorage, player)

            for (playerToSynchronize in playersToSynchronize) {
                if (playerToSynchronize !in state.players && playerToSynchronize.id != player.id) {
                    state.players += playerToSynchronize
                    CLIENTBOUND_FULL_PLAYER_ENDPOINT
                        .sendS2C(
                            FullPlayerPacket(
                                playerToSynchronize.id,
                                FullPlayerData.of(playerToSynchronize)
                            ),
                            player.id
                        )
                }
            }
        }

        server.listWorlds().forEach {
            it.iterate<Networked>() { entity, _ -> entity.clearMetaState() }
        }
    }

    fun onPlayerIntent(context: ScriptContext.IntentExecution, intent: Intent) {
        CLIENTBOUND_INTENT_ENDPOINT.broadcastInRadius(
            context.actor.player,
            playerSynchronizationRadius,
            IntentPacket(intent.id, context.toDto())
        )
    }

    fun onPlayerInteraction(player: EnginePlayer, component: InteractionComponent) = with(player.world) {
        CLIENTBOUND_PLAYER_INTERACTION_PACKET.broadcastInRadius(
            player.location,
            playerSynchronizationRadius,
            packet = PlayerInteractionPacket(
                player.id,
                component.toDto()
            )
        )
    }

    fun onChunkSend(world: World, chunk: EngineChunk, pos: EngineChunkPos, player: EnginePlayer) = with(world) {
        CLIENTBOUND_CHUNK_ENDPOINT.sendS2C(
            EngineChunkPacket(
                EngineChunkDto(
                    pos,
                    chunk.decals.mapKeys { (k, v) -> ImmutableVoxelPos(k) },
                    chunk.hints.mapKeys { (k, v) -> ImmutableVoxelPos(k) },
                    chunk.dynamicVoxels
                        .filterValues { it.hasComponent<Networked>() }
                        .mapNotNull { (pos, entity) ->
                            ImmutableVoxelPos(pos) to componentManager.getNetworkedComponents(entity)
                                .map { it.toCommonDto() }
                        }
                        .toMap()
                )
            ),
            player.id
        )
        player.network.chunks += pos
    }

    fun onScriptsCompiled() {
        CLIENTBOUND_SCRIPT_RECOMPILE_ENDPOINT.broadcast(ScriptsRecompileEndpoint)
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

    fun onPlayerInstantiation(player: EnginePlayer, notifications: List<Notification> = listOf()) = with(player.world) {
        val playerId = player.id
        val packet = PlayerJoinServerPacket(GeneralPlayerData.of(player))

        playerStorage.forEach {
            if (it == player) return@forEach
            CLIENTBOUND_PLAYER_JOIN_ENDPOINT.sendS2C(
                packet,
                it.id
            )
        }

        val network = player.network
        val joinGamePacket = JoinGamePacket(
            ServerPlayerData.of(player),
            ClientboundWorldData.of(this),
            ClientboundSetupData.create(server, player),
            notifications
        )
        CLIENTBOUND_JOIN_GAME_ENDPOINT.sendS2C(joinGamePacket, player.id)
        network.items += joinGamePacket.playerData.referencedItems.all
    }

    fun onPlayerInstantiationConfirm(playerId: PlayerId) = updatePlayer(playerId) {
        network.authorized = true
    }

    fun onPlayerDestroy(player: EnginePlayer) {
        CLIENTBOUND_PLAYER_DESTROY_ENDPOINT.broadcast(
            PlayerDestroyPacket(player.id)
        )
        playerStorage.forEach { it.network.players.remove(player) }
    }

    fun onItemsBatchDestroy(item: List<PersistentId>) {
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