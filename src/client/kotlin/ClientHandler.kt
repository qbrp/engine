package org.lain.engine.client

import kotlinx.coroutines.runBlocking
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.MessageAuthor
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.client.chat.EngineChatMessage
import org.lain.engine.client.transport.ClientAcknowledgeHandler
import org.lain.engine.client.transport.registerClientReceiver
import org.lain.engine.client.transport.sendC2SPacket
import org.lain.engine.client.render.WARNING
import org.lain.engine.client.render.WARNING_COLOR
import org.lain.engine.client.transport.ClientTransportContext
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerId
import org.lain.engine.player.customName
import org.lain.engine.server.AttributeUpdate
import org.lain.engine.server.Notification
import org.lain.engine.transport.packet.CLIENTBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_FULL_PLAYER_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_JOIN_GAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_DESTROY_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_JOIN_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT
import org.lain.engine.transport.packet.CLIENTBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.transport.packet.ClientDefaultAttributes
import org.lain.engine.transport.packet.ClientboundServerSettings
import org.lain.engine.transport.packet.ClientboundSetupData
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.DeveloperModePacket
import org.lain.engine.transport.packet.FullPlayerData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.IncomingChatMessagePacket
import org.lain.engine.transport.packet.SERVERBOUND_CHAT_MESSAGE_ENDPOINT
import org.lain.engine.transport.packet.SERVERBOUND_DEVELOPER_MODE_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_SPEED_INTENTION_PACKET
import org.lain.engine.transport.packet.SERVERBOUND_VOLUME_PACKET
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.transport.packet.SetSpeedIntentionPacket
import org.lain.engine.transport.packet.VolumePacket
import org.lain.engine.util.flush
import org.lain.engine.util.injectValue
import org.lain.engine.util.replace
import org.lain.engine.util.replaceOrSet
import org.lain.engine.util.require
import org.lain.engine.util.set
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.forEach

typealias Update = Player.() -> Unit

typealias PendingUpdates = MutableList<Update>

class ClientHandler(
    private val client: EngineClient
) {
    private val clientAcknowledgeHandler = ClientAcknowledgeHandler()
    private val logger = LoggerFactory.getLogger("Engine Client Handler")
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()
    private val handledNotifications = mutableSetOf<Notification>()
    private val pendingPlayerUpdates = ConcurrentHashMap<PlayerId, PendingUpdates>()
    private val gameSession get() = client.gameSession

    fun run() {
        clientAcknowledgeHandler.run()

        CLIENTBOUND_JOIN_GAME_ENDPOINT.registerClientReceiver { ctx ->
            onJoinGame(playerData, worldData, setupData)
        }

        CLIENTBOUND_PLAYER_ATTRIBUTE_UPDATE_ENDPOINT.registerClientReceiver { ctx ->
            onPlayerAttributeUpdate(id, speed, jumpStrength)
        }

        CLIENTBOUND_SERVER_SETTINGS_UPDATE_ENDPOINT.registerClientReceiver { ctx ->
            onServerSettingsUpdate(settings)
        }

        CLIENTBOUND_PLAYER_CUSTOM_NAME_ENDPOINT.registerClientReceiver { ctx ->
            onPlayerCustomName(id, name)
        }

        CLIENTBOUND_SPEED_INTENTION_PACKET.registerClientReceiver { ctx ->
            onPlayerSpeedIntention(id, speedIntention)
        }

        CLIENTBOUND_PLAYER_JOIN_ENDPOINT.registerClientReceiver { ctx ->
            onPlayerJoined(player)
        }

        CLIENTBOUND_PLAYER_DESTROY_ENDPOINT.registerClientReceiver { ctx ->
            onPlayerDestroyed(playerId)
        }

        CLIENTBOUND_FULL_PLAYER_ENDPOINT.registerClientReceiver { ctx ->
            onFullPlayerData(id, data)
        }

        CLIENTBOUND_CHAT_MESSAGE_ENDPOINT.registerClientReceiver { ctx ->
            val gameSession = client.gameSession ?: return@registerClientReceiver
            val world = gameSession.world
            if (world.id != sourceWorld) {
                logger.error("Пропущено сообщение из-за отсутствия мира источника сообщения $sourceWorld")
                return@registerClientReceiver
            }
            val player = sourcePlayer?.let { gameSession.getPlayer(it) }
            onChatMessage(
                OutcomingMessage(
                    text,
                    MessageSource(
                        world,
                        MessageAuthor(
                            sourceAuthorName,
                            player
                        ),
                        sourcePosition,
                    ),
                    channel,
                    mentioned,
                    speech,
                    volume,
                    placeholders,
                    isSpy
                )
            )
        }

        CLIENTBOUND_PLAYER_NOTIFICATION_ENDPOINT.registerClientReceiver { ctx ->
            onNotification(type, once)
        }
    }

    fun disable() {
        injectValue<ClientTransportContext>().unregisterAll()
    }

    fun onChatMessageSend(content: String, channelId: ChannelId) {
        SERVERBOUND_CHAT_MESSAGE_ENDPOINT.sendC2SPacket(IncomingChatMessagePacket(content, channelId))
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

    fun flushTasks() {
        tasks.flush { it() }
    }

    private fun onPlayerAttributeUpdate(
        player: PlayerId,
        speed: AttributeUpdate? = null,
        jumpStrength: AttributeUpdate? = null
    ) = updatePlayer(player) {
        val attributes = require<PlayerAttributes>()

        applyAttribute(speed) { attributes.speed.custom = it }
        applyAttribute(jumpStrength) { attributes.jumpStrength.custom = it }
    }

    private fun applyAttribute(
        update: AttributeUpdate?,
        setter: (Float?) -> Unit
    ) {
        when (update) {
            AttributeUpdate.Reset -> setter(null)
            is AttributeUpdate.Value -> setter(update.value)
            else -> {}
        }
    }

    private fun onPlayerCustomName(player: PlayerId, customName: String) = updatePlayer(player) {
        this.customName = customName
    }

    private fun onPlayerSpeedIntention(player: PlayerId, intention: Float) = updatePlayer(player) {
        this.require<MovementStatus>().intention = intention
    }

    private fun onFullPlayerData(id: PlayerId, data: FullPlayerData) = updatePlayer(id) {
        replaceOrSet(data.movementStatus)
        replaceOrSet(data.attributes)
        isLowDetailed = false
    }

    private fun onPlayerJoined(data: GeneralPlayerData) = gameSessionTask {
        val player = lowDetailedClientPlayerInstance(data.playerId, world, data)
        instantiatePlayer(player)
        pendingPlayerUpdates[player.id]
            ?.forEach { player.it() }
            ?.also { pendingPlayerUpdates.remove(player.id) }
    }

    private fun onPlayerDestroyed(player: PlayerId) = gameSessionTask {
        playerStorage.remove(player)
    }

    private fun onJoinGame(
        playerData: ServerPlayerData,
        worldData: ClientboundWorldData,
        data: ClientboundSetupData,
    ) = runBlocking {
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

    private fun onServerSettingsUpdate(settings: ClientboundServerSettings) = gameSessionTask {
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

    private fun onChatMessage(message: OutcomingMessage) = gameSessionTask {
        val channelId = message.channel
        val channel = chatManager.getChannel(channelId)
        val text = message.text
        var display = channel.format
        val placeholders = chatManager.settings.placeholders + message.placeholders
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
            val spyDisplay = client.resources.formatConfiguration.spy
            display = spyDisplay.replace("{original}", display)
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
                message.isSpy
            )
        )
    }

    private fun onNotification(type: Notification, once: Boolean) {
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

    private fun updatePlayer(id: PlayerId, update: Update) {
        getPlayer(id)?.update() ?: pendingPlayerUpdates.computeIfAbsent(id) { mutableListOf() }.add(update)
    }

    private fun getPlayer(id: PlayerId): Player? {
        return client.gameSession?.getPlayer(id)
    }

    private fun gameSessionTask(todo: GameSession.() -> Unit) {
        val session = gameSession
        if (session != null) {
            tasks += { session.todo() }
        }
    }
}