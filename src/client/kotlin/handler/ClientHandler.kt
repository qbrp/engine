package org.lain.engine.client.handler

import kotlinx.coroutines.Runnable
import kotlinx.coroutines.runBlocking
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen
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
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.*
import org.lain.engine.server.Notification
import org.lain.engine.transport.packet.*
import org.lain.engine.util.*
import org.lain.engine.util.component.*
import org.lain.engine.world.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class ClientHandler(val client: EngineClient, val eventBus: ClientEventBus) {
    private val gameSession get() = client.gameSession
    private val handledNotifications = mutableSetOf<Notification>()
    private val clientAcknowledgeHandler = ClientAcknowledgeHandler()
    val taskExecutor = TaskExecutor()
    val processedSounds = mutableSetOf<SoundBroadcast>()
    val processedInteractions = FixedSizeList<InteractionId>(40)
    val pendingSnapshots = mutableListOf<Pair<InteractionId, Runnable>>()

    fun run() {
        runEndpoints(clientAcknowledgeHandler)
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
        handledNotifications.clear()
        processedSounds.clear()
    }

    fun tick() {
        val gameSession = gameSession
        if (gameSession != null) {
            SERVERBOUND_CLIENT_TICK_END_ENDPOINT.sendC2SPacket(ClientTickEndPacket)
            val input = gameSession.mainPlayer.require<PlayerInput>()
            val actions = input.actions.toMutableSet()

            if (MinecraftClient.currentScreen !is CreativeInventoryScreen) {
                actions.removeIf { it is InputAction.SlotClick }
            }

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
        taskExecutor.flush()
        clientAcknowledgeHandler.tick()
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
        SERVERBOUND_CURSOR_ITEM_ENDPOINT.sendC2SPacket(CursorItemPacket(item?.uuid))
    }

    fun onChatStartTyping(channelId: ChannelId) {
        SERVERBOUND_CHAT_TYPING_START_ENDPOINT.sendC2SPacket(ChatTypingStartPacket(channelId))
    }

    fun onChatEndTyping() {
        SERVERBOUND_CHAT_TYPING_END_ENDPOINT.sendC2SPacket(ChatTypingEndPacket)
    }

    fun onWriteableContentsUpdate(item: ItemUuid, contents: List<String>) {
        SERVERBOUND_WRITEABLE_UPDATE_ENDPOINT.sendC2SPacket(WriteableUpdatePacket(item, contents))
    }

    fun applyFullPlayerData(player: EnginePlayer, data: FullPlayerData) = with(player) {
        replaceOrSet(data.movementStatus)
        replaceOrSet(data.attributes)
        replaceOrSet(data.armStatus)
        replaceOrSet(data.equipment)
        require<PlayerModel>().skinEyeY = data.skinEyeY
        isLowDetailed = false
        client.eventBus.onFullPlayerData(client, id, data)
    }

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
    ) = runBlocking {
        if (client.gameSession != null) {
            error("Игровая сессия уже запущена!")
        }
        val world = clientWorld(worldData)
        val gameSession = GameSession(
            data.serverId,
            world,
            data,
            playerData,
            this@ClientHandler,
            client
        )

        client.joinGameSession(gameSession)
        gameSession.chatManager.updateSettings(data.settings.chat)
    }

    fun applyServerSettingsUpdate(settings: ClientboundServerSettings) = with(gameSession!!) {
        val defaultAttributes = settings.defaultAttributes
        vocalRegulator.volume.apply {
            max = defaultAttributes.maxVolume
            base = defaultAttributes.baseVolume
        }
        movementDefaultAttributes = defaultAttributes.movement
        movementSettings = settings.movement
        playerSynchronizationRadius = settings.playerSynchronizationRadius
        playerDesynchronizationThreshold = settings.playerDesynchronizationThreshold
        chatManager.updateSettings(settings.chat)
    }

    fun applyChatMessage(message: OutcomingMessage) {
        val chatManager = gameSession?.chatManager ?: return
        chatManager.addMessage(
            acceptOutcomingMessage(
                message,
                chatManager.availableChannels,
                SYSTEM_CHANNEL,
                chatManager.settings.placeholders,
                client.resources.formatConfiguration,
                gameSession!!.playerStorage.map { it.username }
            )
        )
    }

    fun applyDeleteChatMessage(id: MessageId) = with(gameSession!!) {
        chatManager.deleteMessage(id)
    }

    fun applyNotification(type: Notification, once: Boolean) {
        if (!handledNotifications.add(type) && once) return
        val notification = when(type) {
            Notification.COMPILATION_ERROR -> {
                LittleNotification(
                    "Ошибка компиляции сервера",
                    "Проверьте консоль или логи для получения более подробной информации.",
                    WARNING_COLOR,
                    WARNING,
                    lifeTime = 240
                )
            }

            Notification.INVALID_SOURCE_POS ->
                LittleNotification(
                    "Выход за пределы мира",
                    "Сообщение в этой зоне обрабатываются некорректно — используется упрощенная симуляция.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )

            Notification.ACOUSTIC_ERROR -> {
                LittleNotification(
                    "Акустика сломалась",
                    "<red>При обработке сообщения акустической системой возникла ошибка. Ваше сообщения не будет видно другим игрокам.",
                    color = WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 240
                )
            }
            Notification.FREECAM ->
                LittleNotification(
                    "Вы используете мод Freecam",
                    "Его использование способствует получению мета-информации, для игры на сервере он запрещен.",
                    color = FREECAM_WARNING_COLOR,
                    sprite = WARNING,
                    lifeTime = 300
                )
        }

        client.applyLittleNotification(notification)
    }

    fun applyItemPacket(item: ClientboundItemData) = with(gameSession!!) {
        val item = clientItem(world, item)
        fun add() = itemStorage.add(item.uuid, item)

        try {
            add()
        } catch (e: IdCollisionException) {
            itemStorage.remove(item.uuid)
            add()

            LOGGER.warn("Предмет ${item.id} (${item.uuid}) был перезаписан")
        }
    }

    fun applyPlaySoundPacket(play: SoundPlay, context: SoundContext?): Unit = with(gameSession!!) {
        soundsToBroadcast += SoundBroadcast(play, listOf(), context)
    }

    fun applyAcousticDebugVolumePacket(volumes: List<Pair<VoxelPos, Float>>) = with(gameSession!!) {
        acousticDebugVolumes = volumes
        eventBus.onAcousticDebugVolumes(volumes, this)
    }

    private val pendingChunks: Queue<Pair<EngineChunkPos, EngineChunk>> = LinkedList()

    fun applyChunkPacket(pos: EngineChunkPos, chunk: EngineChunk) {
        val session = gameSession
        if (session == null) {
            pendingChunks.add(pos to chunk)
        } else {
            if (pendingChunks.isNotEmpty()) {
                pendingChunks.flush { session.loadChunk(it.first, it.second) }
            }
            session.loadChunk(pos, chunk)
        }
    }

    fun applyVoxelEvent(event: VoxelEvent) = with(gameSession!!) {
        world.emitEvent(event)
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("Engine Client Handler")
    }
}