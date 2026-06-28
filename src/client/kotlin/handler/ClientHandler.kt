package org.lain.engine.client.handler

import kotlinx.coroutines.*
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import org.lain.cyberia.ecs.*
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.ClientEventBus
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.chat.acceptOutcomingMessage
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.commands.ClientCommandIntentBehaviour
import org.lain.engine.player.*
import org.lain.engine.script.EntityDebugData
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue
import org.lain.engine.server.Notification
import org.lain.engine.server.desync
import org.lain.engine.storage.*
import org.lain.engine.transport.packet.*
import org.lain.engine.util.*
import org.lain.engine.world.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.Queue

class ClientHandler(val client: EngineClient, val eventBus: ClientEventBus) {
    private val gameSession get() = client.gameSession
    private val handledNotifications = mutableSetOf<Notification>()
    private val clientAcknowledgeHandler = ClientAcknowledgeHandler()
    val taskExecutor = TaskExecutor()
    val processedSounds = mutableSetOf<SoundBroadcast>()
    val processedInteractions = FixedSizeList<InteractionId>(40)
    val pendingSnapshots = mutableListOf<Pair<InteractionId, Runnable>>()
    val pendingFullPlayerData = mutableListOf<Pair<EnginePlayer, FullPlayerData>>()
    private val pendingChunks: Queue<Pair<EngineChunkPos, EngineChunkDto>> = LinkedList()
    private val pendingEntities: MutableMap<PersistentId, CompletableDeferred<PendingEntity?>> = mutableMapOf()
    private val pendingEntityProvider = PendingEntityProvider(pendingEntities)
    private val coroutineDispatcher = taskExecutor.asCoroutineDispatcher()
    private val entityResolverCoroutineScope = CoroutineScope(coroutineDispatcher + SupervisorJob())
    private val waitingChunks = mutableMapOf<EngineChunkPos, CompletableDeferred<EngineChunk>>()

    private fun newEntityResolver() = EntityResolver(pendingEntityProvider)

    fun run() {
        runEndpoints(clientAcknowledgeHandler)
        CLIENTBOUND_VERIFICATION_ENDPOINT.registerClientReceiver { ctx ->
            client.createLuaContext(server.serverId)
            client.compileScripts()
            SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.sendC2SPacket(
                VerificationResponsePacket(
                    DeveloperModeStatus(client.developerMode, client.acousticDebug),
                    client.namespacedStorage.get().namespaceHashMap
                )
            )
        }
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
        handledNotifications.clear()
        processedSounds.clear()
        pendingEntities.clear()
        pendingSnapshots.clear()
        pendingChunks.clear()
    }

    fun tick() {
        val gameSession = client.gameSession
        if (gameSession != null) {
            with(gameSession.world) {
                SERVERBOUND_CLIENT_TICK_END_ENDPOINT.sendC2SPacket(ClientTickEndPacket)
                val input = gameSession.mainPlayer.require<PlayerInput>()
                val actions = input.actions.toMutableSet()

                if (pendingEntities.isNotEmpty()) {
                    entityResolverCoroutineScope.launch {
                        val entityResolver = newEntityResolver()
                        pendingEntities.forEach { (persistentId, pendingEntity) ->
                            launch {
                                entityResolver.loadEntity(
                                    componentLoadSettings,
                                    pendingEntity.await()?.components ?: return@launch,
                                    persistentId
                                )
                                pendingEntities.remove(persistentId)
                            }
                        }
                    }
                }

                handleInteractions(input, actions, gameSession)

                if (MinecraftClient.screen !is CreativeModeInventoryScreen) {
                    actions.removeIf { it is InputAction.SlotClick }
                }

                processPendingFullPlayerData()
            }
        }
        if (MinecraftClient.connection != null) {
            taskExecutor.flush()
            clientAcknowledgeHandler.tick()
        } else if (taskExecutor.notEmpty()) {
            taskExecutor.clear()
        }
    }

    private suspend fun awaitChunk(gameSession: GameSession, pos: EngineChunkPos): EngineChunk {
        gameSession.world.chunkStorage.getChunk(pos)?.let { return it }
        val deferred = CompletableDeferred<EngineChunk>()
        waitingChunks[pos] = deferred
        return deferred.await()
    }

    context(world: World)
    private fun handleInteractions(input: PlayerInput, actions: Set<InputAction>, gameSession: GameSession) {
        val interaction = gameSession.mainPlayer.get<InteractionComponent>()
        if (input.actions != input.lastActions && interaction?.selection == null) {
            SERVERBOUND_INPUT_PACKET.sendC2SPacket(
                InputPacket(
                    gameSession.ticks,
                    actions.map { it.toDto() }.toSet()
                )
            )
        }

        for (player in gameSession.playerStorage) {
            if (player.has<InteractionComponent>()) continue
            player.get<InteractionQueueComponent>()?.interactions?.poll()?.let {
                player.set(it)
            }
        }
    }

    private fun processPendingFullPlayerData() {
        for (i in pendingFullPlayerData.indices.reversed()) {
            val (player, pendingFullData) = pendingFullPlayerData[i]
            if (pendingFullData.referencedItems.isPresent()) {
                pendingFullPlayerData.removeAt(i)
                applyFullPlayerDataInternal(player, pendingFullData)
            }
        }
    }

    fun postTick() {
        val gameSession = gameSession
        if (gameSession != null) {
            val input = gameSession.mainPlayer.require<PlayerInput>()
            input.actions.clear()
        }
        val snapshotsToRemove = pendingSnapshots
            .filter { (interactionId, _) -> interactionId in processedInteractions }
            .alsoForEach { (id, task) -> task.run() }
        pendingSnapshots.removeAll(snapshotsToRemove)
    }

    data class InteractionQueueComponent(val interactions: Queue<InteractionComponent>) : Component

    // Сделать ожидание предметов
    fun applyInteractionPacket(player: EnginePlayer, interaction: InteractionDto): Unit = with(gameSession!!) {
        player.getOrSet { InteractionQueueComponent(LinkedList()) }.interactions.add(
            interaction.toDomain(itemStorage, playerStorage)
        )
    }

    fun applyPlayerInputPacket(player: EnginePlayer, actions: Set<InputActionDto>) = with(gameSession!!) {
        player.input.clear()
        player.input.addAll(actions.map { it.toDomain(itemStorage) })
    }

    fun applyInteractionSelectionPacket(selection: InteractionSelection) = with(gameSession!!) {
        mainPlayer.require<InteractionComponent>().selection = selection
    }

    fun applyPlayerInteractionSelectionSelectPacket(player: EnginePlayer, variant: String?) = with(gameSession!!) {
        val interaction = player.get<InteractionComponent>() ?: run {
            LOGGER.warn("Был принят выбор $variant взаимодействия игрока $player, однако взаимодействие завершено")
            return
        }
        val variant = interaction.selection?.variants?.firstOrNull { it.id == variant }
        interaction.selectionVariant = variant
        if (variant == null) {
            interaction.selectionCancelled = true
        }
    }

    fun sendServerboundChannelData(persistentId: PersistentId, values: List<ScriptValue>) {
        SERVERBOUND_ENTITY_COMPONENT_RPC_ENDPOINT.sendC2SPacket(
            EntityComponentRpcPacket(persistentId, values)
        )
    }

    fun onBlockHintAdd(voxelPos: VoxelPos, text: String) {
        SERVERBOUND_VOXEL_BLOCK_HINT_PACKET.sendC2SPacket(
            VoxelBlockHintPacket(
                voxelPos,
                VoxelBlockHintPacket.Action.Add(text)
            )
        )
    }

    fun onBlockHintRemove(voxelPos: VoxelPos, index: Int) {
        SERVERBOUND_VOXEL_BLOCK_HINT_PACKET.sendC2SPacket(
            VoxelBlockHintPacket(
                voxelPos,
                VoxelBlockHintPacket.Action.Remove(index)
            )
        )
    }

    fun onEntityDebugView(persistentId: PersistentId) {
        SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT.sendC2SPacket(EntityDebugViewPacket(persistentId))
    }

    fun onEntityDebugViewStop() {
        SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT.sendC2SPacket(EntityDebugViewStopPacket)
    }

    fun onInteractionSelectionSelect(variantId: String?) {
        SERVERBOUND_INTERACTION_SELECTION_SELECT_ENDPOINT.sendC2SPacket(InteractionSelectionSelectPacket(variantId))
    }

    fun onArmStatusUpdate(extend: Boolean) {
        SERVERBOUND_ARM_STATUS_ENDPOINT.sendC2SPacket(ArmStatusPacket(extend))
    }

    fun onChatMessageSend(content: String, channelId: ChannelId) {
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.sendC2SPacket(IncomingChatMessagePacket(content, channelId))
    }

    fun onChatMessageDelete(message: AcceptedMessage) {
        SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT.sendC2SPacket(DeleteChatMessagePacket(message.id))
    }

    fun onVolumeUpdate(volume: Float) {
        SERVERBOUND_VOLUME_PACKET.sendC2SPacket(VolumePacket(volume))
    }

    fun onSpeedIntentionUpdate(value: Float) {
        SERVERBOUND_SPEED_INTENTION_PACKET.sendC2SPacket(SetSpeedIntentionPacket(value))
    }

    fun onDeveloperModeUpdate(boolean: Boolean, acoustic: Boolean) {
        SERVERBOUND_DEVELOPER_MODE_PACKET.sendC2SPacket(
            DeveloperModePacket(DeveloperModeStatus(boolean, acoustic))
        )
    }

    fun onCursorItem(item: EngineItem?) {
        val gameSession = gameSession ?: return
        with(gameSession.world) {
            SERVERBOUND_CURSOR_ITEM_ENDPOINT.sendC2SPacket(
                CursorItemPacket(item?.requireComponent<PersistentIdComponent>()?.id)
            )
        }
    }

    fun onChatStartTyping(channelId: ChannelId) {
        SERVERBOUND_CHAT_TYPING_START_ENDPOINT.sendC2SPacket(ChatTypingStartPacket(channelId))
    }

    fun onChatEndTyping() {
        SERVERBOUND_CHAT_TYPING_END_ENDPOINT.sendC2SPacket(ChatTypingEndPacket)
    }

    fun onWriteableContentsUpdate(item: PersistentId, contents: List<String>) {
        SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT.sendC2SPacket(WriteableUpdatePacket(item, contents))
    }

    fun applyFullPlayerData(player: EnginePlayer, data: FullPlayerData) {
        if (!data.referencedItems.isPresent()) {
            pendingFullPlayerData += player to data
        } else {
            applyFullPlayerDataInternal(player, data)
        }
    }

    private fun applyFullPlayerDataInternal(player: EnginePlayer, data: FullPlayerData) = with(player) {
        replaceOrSet(data.movementStatus)
        replaceOrSet(data.attributes)
        replaceOrSet(data.armStatus)
        require<PlayerModel>().skinEyeY = data.skinEyeY
        isLowDetailed = false
        client.eventBus.onFullPlayerData(client, id, data)
    }

    private fun PlayerReferencedItems.isPresent() = all.none { gameSession?.itemStorage?.get(it) == null }

    fun applyPlayerJoined(data: GeneralPlayerData) {
        gameSession!!.instantiateLowDetailedPlayer(data)
    }

    fun applyPlayerDestroyed(player: EnginePlayer) {
        gameSession!!.playerStorage.remove(player.id)
        eventBus.onPlayerDestroy(client, player.id)
    }

    fun applyJoinGame(
        playerData: ServerPlayerData,
        worldData: ClientboundWorldData,
        data: ClientboundSetupData,
        notifications: List<Notification>
    ) = runBlocking {
        if (client.gameSession != null) {
            error("Игровая сессия уже запущена!")
        }

        val gameSession = GameSession(
            data.serverId,
            data,
            worldData,
            playerData,
            this@ClientHandler,
            client
        )

        client.joinGameSession(gameSession)
        gameSession.chatManager.updateSettings(data.settings.chat)
        notifications.forEach { applyNotification(it, false) }
        SERVERBOUND_JOIN_CONFIRMATION_ENDPOINT.sendC2SPacket(ConfirmationPacket)
    }

    fun applyServerSettingsUpdate(settings: ClientboundServerSettings) = with(gameSession!!) {
        val defaultAttributes = settings.defaultAttributes
        vocalRegulator.volume.apply {
            max = defaultAttributes.maxVolume
            base = defaultAttributes.baseVolume
        }
        movementDefaultAttributes = defaultAttributes.movement
        movementSettings = settings.movement
        synchronizationRadius = settings.synchronizationRadius
        playerDesynchronizationThreshold = settings.playerDesynchronizationThreshold
        chatManager.updateSettings(settings.chat)
    }

    fun applyChatMessage(gameSession: GameSession, message: OutcomingMessage) {
        val chatManager = gameSession.chatManager ?: return
        chatManager.addMessage(
            acceptOutcomingMessage(
                message,
                chatManager.availableChannels,
                SYSTEM_CHANNEL,
                chatManager.settings.placeholders,
                client.resources.formatConfiguration,
                gameSession.playerStorage.map { it.username }
            )
        )
    }

    fun applyDeleteChatMessage(id: MessageId) = with(gameSession!!) {
        chatManager.deleteMessage(id)
    }

    fun applyNotification(type: Notification, once: Boolean) {
        if (!handledNotifications.add(type) && once) return
        client.applyLittleNotification(LittleNotification.ofServer(type))
    }

    fun applyPlaySoundPacket(play: SoundPlay, context: SoundContext?): Unit = with(gameSession!!) {
        soundsToBroadcast += SoundBroadcast(play, listOf(), context)
    }

    fun applyAcousticDebugVolumePacket(volumes: List<Pair<VoxelPos, Float>>) = with(gameSession!!) {
        acousticDebugVolumes = volumes
        eventBus.onAcousticDebugVolumes(volumes, this)
    }

    fun applyChunkPacket(chunkDto: EngineChunkDto) {
        val session = gameSession
        val pos = chunkDto.pos
        if (session == null) {
            pendingChunks.add(pos to chunkDto)
        } else {
            if (pendingChunks.isNotEmpty()) {
                pendingChunks.flush { session.loadChunk(it.second) }
            }
            session.loadChunk(chunkDto)
        }
    }

    private fun GameSession.loadChunk(chunkDto: EngineChunkDto) = with(gameSession!!.world) {
        val pos = chunkDto.pos
        val chunk = EngineChunk(
            chunkDto.decals.toMutableMap(),
            chunkDto.hints.toMutableMap(),
            mutableMapOf()
        )
        loadChunk(pos, chunk)
        waitingChunks.remove(pos)?.complete(chunk)
    }

    fun applyDynamicVoxelDelta(gameSession: GameSession, voxelPos: VoxelPos, components: List<ComponentDto>) = with(gameSession.world) {
        val entity = chunkStorage.getDynamicVoxel(voxelPos) ?: run {
            val entity = addEntity()
            entity.setDynamicVoxel(voxelPos, false)
            entity
        }

        entityResolverCoroutineScope.launch {
            entity.copyState(
                components.toDomainSuspend {
                    toDomain(
                        componentLoadSettings,
                        entityGetter = { null },
                    )
                }
            )
            val chunk = awaitChunk(gameSession, EngineChunkPos(voxelPos))
            chunk.dynamicVoxels[voxelPos] = entity
        }
    }

    fun applyVoxelEvent(event: VoxelEvent) = with(gameSession!!) {
        world.emitEvent(event)
    }

    fun applyEntity(gameSession: GameSession, persistentId: PersistentId, components: List<ComponentDto>) {
        if (persistentId is VoxelPosId) {
            LOGGER.warn("Синхронизация блока $persistentId как обычной сущность проигнорирована")
            return
        }
        val pendingEntity = PendingEntity(components)
        pendingEntities[persistentId] = CompletableDeferred(pendingEntity)
    }

    fun applyEntityDebugData(data: EntityDebugData.Dto) {
        client.eventBus.onEntityDebugViewData(data)
    }

    fun applyIntent(dto: IntentExecuteDto, intentId: IntentId) = with(gameSession!!) {
        val intent = namespacedStorage.intents[intentId] ?: desync("Интент $intentId не существует")
        val actor = dto.actor.let { actor ->
            val enginePlayer = getPlayer(actor.player) ?: error("Can't find intent actor ${actor.player}")
            IntentActor(
                actor.type,
                enginePlayer,
                enginePlayer.entityId
            )
        }
        val target = dto.target?.let {
            IntentTarget(
                it.player?.let { id -> getPlayer(id) },
                it.voxelPos,
                it.pos
            )
        }
        val behaviour = when (val behaviour = dto.behaviour) {
            is IntentBehaviourDto.Command -> ClientCommandIntentBehaviour(actor.player)
        }
        executeIntent(intent, ScriptContext.IntentExecution(actor, target, dto.inputValues.map { it.toDomain() }, behaviour), namespacedStorage)
    }

    fun applyWorldState(gameSession: GameSession, components: List<ComponentDto>) = with(gameSession.world) {
        runBlocking {
            state.copyComponentDtoState(components) { toDomainWithoutRelationships(itemStorage, namespacedStorage) }
        }
    }

    fun applyItemUnload(gameSession: GameSession, items: List<PersistentId>) = with(gameSession.world) {
        items.forEach { item -> gameSession.itemStorage.remove(item)?.destroy() }
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("Engine Client Handler")
    }
}