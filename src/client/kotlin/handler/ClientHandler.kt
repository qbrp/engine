package org.lain.engine.client.handler

import kotlinx.coroutines.runBlocking
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageId
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.ClientEventBus
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.client.transport.clientWorld
import org.lain.engine.client.transport.isLowDetailed
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.render.WARNING
import org.lain.engine.util.WARNING_COLOR
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.customName
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.Notification
import org.lain.engine.transport.packet.ClientboundServerSettings
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.DeleteChatMessagePacket
import org.lain.engine.transport.packet.DeveloperModePacket
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.IncomingChatMessagePacket
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DELETE_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.transport.packet.SetSpeedIntentionPacket
import org.lain.engine.transport.packet.VolumePacket
import org.lain.engine.util.injectValue
import org.lain.engine.util.replaceOrSet
import org.lain.engine.util.require
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.collections.forEach
import kotlin.collections.plus

typealias Update = Player.() -> Unit

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
        taskExecutor.flush(client)
    }

    fun onChatMessageSend(content: String, channelId: ChannelId) {
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.sendC2SPacket(IncomingChatMessagePacket(content, channelId))
    }

    fun onChatMessageDelete(message: EngineChatMessage) {
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

    fun applyPlayerAttributeUpdate(
        player: Player,
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

    fun applyPlayerCustomName(player: Player, customName: String?) = with(player) {
        this.customName = customName
    }

    fun applyPlayerSpeedIntention(player: Player, intention: Float) = with(player) {
        this.require<MovementStatus>().intention = intention
    }

    fun applyFullPlayerData(player: Player, data: FullPlayerData) = with(player) {
        replaceOrSet(data.movementStatus)
        replaceOrSet(data.attributes)
        isLowDetailed = false
        client.eventBus.onFullPlayerData(client, id, data)
    }

    fun applyPlayerJoined(data: GeneralPlayerData) {
        gameSession!!.instantiateLowDetailedPlayer(data)
    }

    fun applyPlayerDestroyed(player: Player) {
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

    fun applyChatMessage(message: OutcomingMessage) = with(gameSession!!) {
        val channelId = message.channel
        val channel = chatManager.getChannel(channelId)
        val chatFormat = client.resources.formatConfiguration
        var text = message.text

        chatFormat.regex.replace.forEach { rule ->
            val regex = Regex(rule.exp)

            text = regex.replace(text) { match ->
                val value = match.value
                val cleaned = rule.remove?.let {
                    value.replace(it, "")
                } ?: value

                rule.value.replace("{match}", cleaned)
            }
        }

        var display = channel.format
        val placeholders = (chatManager.settings.placeholders + message.placeholders).toMutableMap()
        placeholders["text"] = text
        placeholders.forEach { old, new ->
            display = display
                .replace("{$old}", new)
        }

        display = Regex("\\{([^|{}]+)\\|([^{}]*)\\}").replace(display) { match ->
            val name = match.groupValues[1]
            val defaultValue = match.groupValues.getOrNull(2)
            val placeholder = placeholders[name]

            placeholder ?: (defaultValue ?: "")
        }

        if (message.isSpy) {
            display = chatFormat.spy.replace("{original}", display)
        }

        chatManager.addMessage(
            EngineChatMessage(
                text,
                display,
                channel,
                message.source,
                message.mentioned,
                message.speech,
                message.volume,
                message.isSpy,
                message.head,
                message.id
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

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("Engine Client Handler")
    }
}