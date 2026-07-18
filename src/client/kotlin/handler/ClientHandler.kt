package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.cyberia.ecs.*
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.ClientEventListener
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
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.commands.ClientCommandIntentBehaviour
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.script.EntityDebugData
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.server.Notification
import org.lain.engine.server.desync
import org.lain.engine.storage.*
import org.lain.engine.transport.packet.*
import org.lain.engine.util.*
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClientHandler(val client: EngineClient, val eventBus: ClientEventListener) {
    private val gameSession get() = client.gameSession
    private val clientAcknowledgeHandler = ClientAcknowledgeHandler()

    val taskExecutor = TaskExecutor()

    private val showedNotifications = mutableSetOf<Notification>()
    val processedInteraction = mutableSetOf<InteractionIdentity>()

    data class InteractionIdentity(val tick: Long, val entity: EntityId)

    private val coroutineDispatcher = taskExecutor.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    private val awaitingEntities: MutableMap<PersistentId, CompletableDeferred<PendingEntity?>> = mutableMapOf()
    private val awaitingChunks = mutableMapOf<EngineChunkPos, CompletableDeferred<EngineChunk>>()
    private val pendingEntityProvider = PendingEntityProvider(awaitingEntities)
    private var characterApplyConfirmationCompletableDeferred: CompletableDeferred<Unit>? = null

    private fun newEntityResolver() = EntityResolver(pendingEntityProvider)

    fun awaitCharacterApplyConfirmation(): CompletableDeferred<Unit> {
        val completableDeferred = CompletableDeferred<Unit>()
        characterApplyConfirmationCompletableDeferred = completableDeferred
        return completableDeferred
    }

    fun applyCharacterApplyConfirmation() {
        characterApplyConfirmationCompletableDeferred?.complete(Unit)
    }

    fun run() {
        runEndpoints(clientAcknowledgeHandler)
        CLIENTBOUND_VERIFICATION_ENDPOINT.registerClientReceiver { ctx ->
            client.createLuaContext(server.serverId)
            client.compileScripts()

            val namespaceHashMap = client.namespacedStorage.get().namespaceHashMap
            if (server.requireIdenticalNamespaces) {
                val result = validateNamespaceHashMap(namespaceHashMap, server.namespaceHashMap)
                if (result is NamespaceHashMapValidationResult.Error) {
                    friendlyError(result.computeErrorMessage())
                }
            }

            SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.sendC2SPacket(
                VerificationResponsePacket(
                    DeveloperModeStatus(client.developerMode, client.acousticDebug),
                    namespaceHashMap,
                    ""
                )
            )
        }
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
        showedNotifications.clear()
        awaitingEntities.clear()
        processedInteraction.clear()
        awaitingChunks.clear()
        pendingEntityProvider.clear()
    }

    fun tick() {
        val gameSession = client.gameSession
        if (gameSession != null) {
            with(gameSession.world) {
                val input = gameSession.mainPlayer.entity.requireComponent<PlayerInput>()
                val actions = input.actions.toMutableSet()
                handlePlayerInput(input, actions, gameSession)
            }
        }
        if (MinecraftClient.connection != null) {
            taskExecutor.flush()
            clientAcknowledgeHandler.tick()
        } else if (taskExecutor.notEmpty()) {
            taskExecutor.clear()
        }

        MinecraftClientDispatcher.confirmTick()
    }

    fun postTick() {
        val gameSession = gameSession
        if (gameSession != null) {
            val input = with(gameSession.world) { gameSession.mainPlayer.entity.requireComponent<PlayerInput>() }
            input.actions.clear()
        }
    }

    context(world: World)
    private fun handlePlayerInput(input: PlayerInput, actions: Set<InputAction>, gameSession: GameSession) {
        input.tick = gameSession.ticks
        if (input.actions != input.lastActions) {
            SERVERBOUND_INPUT_PACKET.sendC2SPacket(
                InputPacket(
                    gameSession.ticks,
                    actions.toSet()
                )
            )
        }
    }

    private suspend fun awaitChunk(gameSession: GameSession, pos: EngineChunkPos): EngineChunk {
        gameSession.world.chunkStorage.getChunk(pos)?.let { return it }
        val deferred = CompletableDeferred<EngineChunk>()
        awaitingChunks[pos] = deferred
        return deferred.await()
    }

    private suspend fun waitNextTick() = MinecraftClientDispatcher.waitNextTick()

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

    fun onCharacterSelectedSingleplayer(character: EngineCharacter) {
        SERVERBOUND_CHARACTER_APPLY_ENDPOINT.sendC2SPacket(
            CharacterApplyPacket(character.profile.id, character)
        )
    }

    fun onCharacterSelectedMultiplayer(character: EngineCharacter) {
        SERVERBOUND_CHARACTER_APPLY_ENDPOINT.sendC2SPacket(
            CharacterApplyPacket(character.profile.id, null)
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
        if (Thread.currentThread() != gameSession.client.thread) return
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

    fun applyFullPlayerData(player: EnginePlayer, data: FullPlayerData) = coroutineScope.launch {
        while (!data.referencedItems.isPresent()) {
            waitNextTick()
        }

        player.set(data.movementStatus)
        player.set(data.attributes)
        player.set(data.armStatus)
        player.require<EnginePlayerModel>().skinEyeY = data.skinEyeY
        player.isLowDetailed = false
        client.eventListener.onFullPlayerData(client, player.id, data)
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
        if (!showedNotifications.add(type) && once) return
        client.applyLittleNotification(LittleNotification.ofServer(type))
    }

    fun applyPlaySoundPacket(play: SoundPlay, ignorePhysics: Boolean): Unit = with(gameSession!!) {
        client.audioManager.playSound(play, ignorePhysics)
    }

    fun applyAcousticDebugVolumePacket(volumes: List<Pair<VoxelPos, Float>>) = with(gameSession!!) {
        acousticDebugVolumes = volumes
        eventBus.onAcousticDebugVolumes(volumes, this)
    }

    fun applyChunkPacket(chunkDto: EngineChunkDto) = coroutineScope.launch {
        val session = gameSession
        while (session == null) {
            waitNextTick()
        }
        session.loadChunk(chunkDto)
    }

    private fun GameSession.loadChunk(chunkDto: EngineChunkDto) = with(gameSession!!.world) {
        val pos = chunkDto.pos
        val chunk = EngineChunk(
            chunkDto.decals.toMutableMap(),
            chunkDto.hints.toMutableMap(),
            mutableMapOf()
        )
        loadChunk(pos, chunk)
        awaitingChunks.remove(pos)?.complete(chunk)
    }

    fun applyDynamicVoxelDelta(gameSession: GameSession, voxelPos: VoxelPos, components: List<ComponentDto>) = with(gameSession.world) {
        val entity = chunkStorage.getDynamicVoxel(voxelPos) ?: run {
            val entity = addEntity()
            entity.setDynamicVoxel(voxelPos, false)
            entity
        }

        coroutineScope.launch {
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

    fun applyEntity(gameSession: GameSession, persistentId: PersistentId, components: List<ComponentDto>) = with(gameSession.world) {
        if (persistentId is VoxelPosId) {
            LOGGER.warn("Синхронизация блока $persistentId как обычной сущность проигнорирована")
            return@with
        }
        val pendingEntity = PendingEntity(components)
        awaitingEntities[persistentId] = CompletableDeferred(pendingEntity)
        coroutineScope.launch {
            val entity = newEntityResolver().loadEntity(
                componentLoadSettings,
                components,
                persistentId
            )
            awaitingEntities.remove(persistentId)
            gameSession.logInMainThread {
                Log(
                    LogMessages.ENTITY_SYNC_ADD,
                    LogLevel.INFO,
                    data = mapOf(
                        "entity" to entity.getEntityDebugNameId().name,
                        "persistent_id" to persistentId.toString(),
                        "components" to components.joinToString(),
                    ),
                    world = gameSession.world.id,
                    tick = it
                )
            }
        }
    }

    fun applyEntityDebugData(data: EntityDebugData.Dto) {
        client.eventListener.onEntityDebugViewData(data)
    }

    fun applyIntent(dto: IntentExecuteDto, intentId: IntentId) = with(gameSession!!) {
        val intent = namespacedStorage.intents[intentId] ?: desync("Интент $intentId не существует")
        val actor = dto.actor.let { actor ->
            val enginePlayer = getPlayer(actor.player) ?: error("Can't find intent actor ${actor.player}")
            IntentActor(
                actor.type,
                enginePlayer,
                enginePlayer.entity
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
