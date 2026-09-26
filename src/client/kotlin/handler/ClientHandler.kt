package org.lain.engine.client.handler

import kotlinx.coroutines.*
import org.lain.cyberia.ecs.*
import org.lain.engine.Constants.ENGINE_MOD_VERSION
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.ClientPlatform
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.chat.acceptOutcomingMessage
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.client.util.withClientContext
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.commands.ClientCommandOperationBehaviour
import org.lain.engine.mc.server.AuthPacket
import org.lain.engine.mc.server.SERVERBOUND_AUTH_ENDPOINT
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.interaction.InputAction
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.player.interaction.PredictionSink
import org.lain.engine.script.EntityDebugData
import org.lain.engine.script.NamespaceHashMap
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue
import org.lain.engine.server.Notification
import org.lain.engine.server.replication.ReplicationFrame
import org.lain.engine.server.protocolError
import org.lain.engine.data.*
import org.lain.engine.transport.packet.*
import org.lain.engine.util.*
import org.lain.engine.world.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClientHandler(val client: EngineClient, val eventBus: ClientPlatform) : PredictionSink {
    private val gameSession get() = client.gameSession

    val taskExecutor = TaskExecutor()

    private val showedNotifications = mutableSetOf<Notification>()

    val processedInteraction = linkedSetOf<InteractionId>()

    internal val coroutineDispatcher = taskExecutor.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    override fun begin(
        world: World,
        entity: EntityId,
        interactionId: InteractionId
    ) {
        rememberProcessedInteraction(interactionId)
        val replication = gameSession?.replicationController
            ?: error("Prediction started without an active game session")
        check(gameSession?.world === world) { "Prediction started in an inactive world" }
        replication.beginPrediction(entity, interactionId)
    }

    fun endInteractionPrediction() {
        gameSession?.replicationController?.endPrediction()
    }

    fun rememberProcessedInteraction(interactionId: InteractionId) {
        processedInteraction += interactionId
        while (processedInteraction.size > MAX_PROCESSED_INTERACTIONS) {
            processedInteraction.remove(processedInteraction.first())
        }
    }

    fun applyCharacterApplyConfirmation(requestId: Long, errorMessage: String?) {
        gameSession?.characterChange?.confirm(requestId, errorMessage)
    }

    fun run() {
        runEndpoints()
        CLIENTBOUND_VERIFICATION_ENDPOINT.registerClientReceiver { ctx ->
            client.joinFlow?.verificationStateStartCompletableDeferred?.complete(server)
        }
    }

    suspend fun sendAuthPacket(modIds: List<String>) = withClientContext {
        SERVERBOUND_AUTH_ENDPOINT
            .sendC2SPacket(
                AuthPacket(
                    modIds,
                    ENGINE_MOD_VERSION
                )
            )
    }

    suspend fun sendVerificationPacket(
        namespaceHashMap: NamespaceHashMap,
        selectedCharacter: EngineCharacter?,
        sessionTicket: SessionTicket,
    ) = withClientContext {
        SERVERBOUND_VERIFICATION_RESPONSE_ENDPOINT.sendC2SPacket(
            VerificationResponsePacket(
                developerModeStatus = DeveloperModeStatus(client.developerMode, client.acousticDebug),
                namespaces = namespaceHashMap,
                characterId = selectedCharacter?.profile?.id,
                sessionTicket = sessionTicket.toDto(),
            )
        )
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
        showedNotifications.clear()
        processedInteraction.clear()
    }

    fun tick() {
        endInteractionPrediction()
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
        } else if (taskExecutor.notEmpty()) {
            taskExecutor.clear()
        }

        MinecraftClientDispatcher.confirmTick()
    }

    fun postTick() {
        val gameSession = gameSession
        if (gameSession != null) {
            val input =
                with(gameSession.world) { gameSession.mainPlayer.entity.requireComponent<PlayerInput>() }
            input.actions.clear()
        }
    }

    context(world: World)
    private fun handlePlayerInput(
        input: PlayerInput,
        actions: Set<InputAction>,
        gameSession: GameSession
    ) {
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

    fun onCharacterSelectedSingleplayer(character: EngineCharacter, requestId: Long) {
        SERVERBOUND_CHARACTER_APPLY_ENDPOINT.sendC2SPacket(
            CharacterApplyPacket(character.profile.id, character, requestId = requestId)
        )
    }

    fun onCharacterSelectedMultiplayer(
        character: EngineCharacter,
        sessionTicket: SessionTicket,
        requestId: Long,
    ) {
        SERVERBOUND_CHARACTER_APPLY_ENDPOINT.sendC2SPacket(
            CharacterApplyPacket(character.profile.id, null, sessionTicket.toDto(), requestId)
        )
    }

    fun onLookSelected(lookId: String, requestId: Long) {
        SERVERBOUND_LOOK_APPLY_ENDPOINT.sendC2SPacket(LookApplyPacket(lookId, requestId))
    }

    fun onEntityDebugView(persistentId: PersistentId) {
        SERVERBOUND_ENTITY_DEBUG_VIEW_ENDPOINT.sendC2SPacket(EntityDebugViewPacket(persistentId))
    }

    fun onEntityDebugViewStop() {
        SERVERBOUND_ENTITY_DEBUG_VIEW_STOP_ENDPOINT.sendC2SPacket(EntityDebugViewStopPacket)
    }

    fun onInteractionSelectionSelect(variantId: String?) {
        SERVERBOUND_INTERACTION_SELECTION_SELECT_ENDPOINT.sendC2SPacket(
            InteractionSelectionSelectPacket(variantId)
        )
    }

    fun onArmStatusUpdate(extend: Boolean) {
        SERVERBOUND_ARM_STATUS_ENDPOINT.sendC2SPacket(ArmStatusPacket(extend))
    }

    fun onChatMessageSend(content: String, channelId: ChannelId) {
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.sendC2SPacket(
            IncomingChatMessagePacket(
                content,
                channelId
            )
        )
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

    fun applyFullPlayerData(gameSession: GameSession, player: EnginePlayer, data: FullPlayerData) = coroutineScope.launch {
        while (!data.referencedItems.isPresent()) {
            waitNextTick()
        }
        player.set(data.movementStatus)
        player.set(data.attributes)
        player.get<EnginePlayerModel>()?.skinEyeY = data.skinEyeY
        player.isLowDetailed = false
        client.infrastructure.onFullPlayerData(gameSession, player, data)
    }

    private fun PlayerReferencedItems.isPresent() =
        all.none { gameSession?.itemStorage?.get(it) == null }

    fun applyPlayerJoined(data: GeneralPlayerData, gameSession: GameSession) {
        processedInteraction.removeIf { it.source == data.playerId }
        val persistentId = CustomPersistentId(data.playerId.toString())
        gameSession.replicationController.removeEntity(persistentId)
        gameSession.instantiateLowDetailedPlayer(data)
    }

    fun applyPlayerDestroyed(gameSession: GameSession, player: EnginePlayer) {
        processedInteraction.removeIf { it.source == player.id }
        gameSession.removePlayer(player)
    }

    fun applyJoinGame(joinGamePacket: JoinGamePacket) {
        if (client.joinFlow?.confirmJoinGamePacket(joinGamePacket) != true) return
        if (client.gameSession != null) {
            error("Игровая сессия уже запущена!")
        }
    }

    fun applyServerSettingsUpdate(settings: ClientboundServerSettings) = with(gameSession!!) {
        val defaultAttributes = settings.defaultAttributes
        vocalRegulator.volume.apply {
            max = defaultAttributes.maxVolume
            base = defaultAttributes.baseVolume
        }
        synchronizationRadius = settings.synchronizationRadius
        playerDesynchronizationThreshold = settings.playerDesynchronizationThreshold
        chatManager.updateSettings(settings.chat)
    }

    fun applyChatMessage(gameSession: GameSession, message: OutcomingMessage) {
        val chatManager = gameSession.chatManager
        chatManager.addMessage(
            acceptOutcomingMessage(
                message,
                chatManager.availableChannels,
                SYSTEM_CHANNEL,
                chatManager.settings.placeholders,
                client.resources.formatConfiguration,
                gameSession.playerStorage.all.map { it.username }
            )
        )
    }

    fun applyDeleteChatMessage(id: MessageId) = with(gameSession!!) {
        chatManager.deleteMessage(id)
    }

    fun applyNotification(type: Notification, once: Boolean) {
        if (!showedNotifications.add(type) && once) return
        client.showNotification(LittleNotification.ofServer(type))
    }

    fun applyPlaySoundPacket(play: SoundPlay, ignorePhysics: Boolean): Unit = with(gameSession!!) {
        client.audioManager.playSound(play, ignorePhysics)
    }

    fun applyAcousticDebugVolumePacket(volumes: List<Pair<VoxelPos, Float>>) = with(gameSession!!) {
        acousticDebugVolumes = volumes
        eventBus.onAcousticDebugVolumes(volumes, this)
    }

    fun applyChunkPacket(chunkDto: EngineChunkDto) = coroutineScope.launch {
        while (gameSession == null) {
            waitNextTick()
        }
        gameSession!!.loadChunk(chunkDto)
    }

    private fun GameSession.loadChunk(chunkDto: EngineChunkDto) = with(gameSession!!.world) {
        val pos = chunkDto.pos
        val chunk = EngineChunk(
            chunkDto.decals.toMutableMap(),
            chunkDto.hints.toMutableMap(),
            mutableMapOf()
        )
        loadChunk(pos, chunk)
    }

    fun applyVoxelEvent(event: VoxelEvent) = with(gameSession!!) {
        world.emitEvent(event)
    }

    fun applyReplicationFrame(gameSession: GameSession, frame: ReplicationFrame) =
        gameSession.replicationController.apply(frame)

    fun applyEntityDebugData(data: EntityDebugData.Dto) {
        client.infrastructure.onEntityDebugViewData(data)
    }

    fun applyOperation(dto: OperationExecuteDto, operationId: OperationId) = with(gameSession!!) {
        val operation = namespacedStorage.operations[operationId]
            ?: protocolError("Операция $operationId не существует")
        val actor = dto.actor.let { actor ->
            val enginePlayer =
                getPlayer(actor.player) ?: error("Can't find operation actor ${actor.player}")
            OperationActor(
                actor.type,
                enginePlayer,
                enginePlayer.entity
            )
        }
        val target = dto.target?.let {
            OperationTarget(
                it.player?.let { id -> getPlayer(id) },
                it.voxelPos,
                it.pos
            )
        }
        val behaviour = when (val behaviour = dto.behaviour) {
            is OperationBehaviourDto.Command -> ClientCommandOperationBehaviour(actor.player)
        }
        operation.execute(
            ScriptContext.OperationExecution(
                actor,
                target,
                dto.inputValues.map { it.toDomain() },
                behaviour
            )
        )
    }

    companion object {
        private const val MAX_PROCESSED_INTERACTIONS = 4096
        val LOGGER: Logger = LoggerFactory.getLogger("Engine Client Handler")
    }
}
