package org.lain.engine.client.handler

import kotlinx.coroutines.runBlocking
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.ClientEventBus
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.chat.SYSTEM_CHANNEL
import org.lain.engine.client.chat.acceptOutcomingMessage
import org.lain.engine.client.transport.clientWorld
import org.lain.engine.client.transport.isLowDetailed
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.render.WARNING
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.transport.clientItem
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.item.EngineItem
import org.lain.engine.item.Gun
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.SoundPlay
import org.lain.engine.player.CustomName
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Interaction
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.customName
import org.lain.engine.player.username
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.Notification
import org.lain.engine.transport.packet.ClientboundItemData
import org.lain.engine.transport.packet.ClientboundServerSettings
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.DeleteChatMessagePacket
import org.lain.engine.transport.packet.DeveloperModePacket
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.IncomingChatMessagePacket
import org.lain.engine.transport.packet.InteractionPacket
import org.lain.engine.transport.packet.ItemGunPacket
import org.lain.engine.transport.packet.ItemPacket
import org.lain.engine.transport.packet.PlayerCursorItemPacket
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_INTERACTION_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_PLAYER_CURSOR_ITEM_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.transport.packet.ServerboundInteractionData
import org.lain.engine.transport.packet.SetSpeedIntentionPacket
import org.lain.engine.transport.packet.VolumePacket
import org.lain.engine.util.IdCollisionException
import org.lain.engine.util.apply
import org.lain.engine.util.injectValue
import org.lain.engine.util.replaceOrSet
import org.lain.engine.util.require
import org.slf4j.Logger
import org.slf4j.LoggerFactory

typealias Update = EnginePlayer.() -> Unit

typealias PendingUpdates = MutableList<Update>

class ClientHandler(val client: EngineClient, val eventBus: ClientEventBus) {
    private val gameSession get() = client.gameSession
    private val handledNotifications = mutableSetOf<Notification>()
    private val clientAcknowledgeHandler = ClientAcknowledgeHandler()
    val taskExecutor = TaskExecutor()

    fun run() {
        runEndpoints(clientAcknowledgeHandler)
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
    }

    fun tick() {
        taskExecutor.flush()
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

    fun onDeveloperModeUpdate(boolean: Boolean) {
        SERVERBOUND_DEVELOPER_MODE_PACKET.sendC2SPacket(DeveloperModePacket(boolean))
    }

    fun onInteraction(interaction: Interaction) {
        SERVERBOUND_INTERACTION_ENDPOINT.sendC2SPacket(
            InteractionPacket(
                ServerboundInteractionData.from(interaction)
            )
        )
    }

    fun onCursorItem(item: EngineItem?) {
        SERVERBOUND_PLAYER_CURSOR_ITEM_ENDPOINT.sendC2SPacket(PlayerCursorItemPacket(item?.uuid))
    }

    fun applyPlayerAttributeUpdate(
        player: EnginePlayer,
        speed: AttributeUpdate? = null,
        jumpStrength: AttributeUpdate? = null
    ) = with(player) {
        val attributes = require<PlayerAttributes>()

        applyAttribute(speed) { attributes.speed.custom = it }
        applyAttribute(jumpStrength) { attributes.jumpStrength.custom = it }
    }

    fun applyAttribute(
        update: AttributeUpdate?,
        setter: (Float?) -> Unit
    ) {
        when (update) {
            AttributeUpdate.Reset -> setter(null)
            is AttributeUpdate.Value -> setter(update.value)
            else -> {}
        }
    }

    fun applyPlayerCustomName(player: EnginePlayer, customName: CustomName?) = with(player) {
        this.customName = customName
    }

    fun applyPlayerSpeedIntention(player: EnginePlayer, intention: Float) = with(player) {
        this.require<MovementStatus>().intention = intention
    }

    fun applyFullPlayerData(player: EnginePlayer, data: FullPlayerData) = with(player) {
        replaceOrSet(data.movementStatus)
        replaceOrSet(data.attributes)
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
            Notification.INVALID_SOURCE_POS ->
                LittleNotification(
                    "Выход за пределы мира",
                    "Сообщение в этой зоне обрабатываются некорректно — используется упрощенная симуляция.",
                    color = WARNING_COLOR,
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

    fun applyItemGunPacket(uuid: ItemUuid, selector: Boolean?, barrelBullets: Int?): Unit = with(gameSession!!) {
        val item = itemStorage.get(uuid) ?: run {
            LOGGER.warn("Прислан пакет данных для несуществующего предмета $uuid")
            return
        }

        item.apply<Gun> {
            barrelBullets?.let { barrel.bullets = it }
            selector?.let { this.selector = it }
        }
    }

    fun applyPlaySoundPacket(play: SoundPlay) {
        client.audioManager.playSound(play)
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("Engine Client Handler")
    }
}