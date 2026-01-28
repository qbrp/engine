package org.lain.engine.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.mc.InvalidMessageSourcePositionException
import org.lain.engine.player.Player
import org.lain.engine.player.chatHeadsEnabled
import org.lain.engine.player.displayName
import org.lain.engine.player.isSpectating
import org.lain.engine.player.username
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.util.Pos
import org.lain.engine.util.filterNearestPlayers
import org.lain.engine.util.roundToInt
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.players
import org.lain.engine.world.pos
import org.lain.engine.world.world
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.text.StringBuilder

class EngineChat(
    private val acousticSimulation: AcousticSimulator,
    private val server: EngineServer
) {
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) { r ->
        Thread(r, "Engine Chat Worker").apply { isDaemon = true }
    }
    private val coroutineScope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())
    private val players
        get() = server.playerStorage

    private val incomingMessageHistory = mutableListOf<IncomingMessage>()
    val outcomingMessageHistory = mutableMapOf<MessageId, OutcomingMessage>()

    private var channelsMap = mapOf<ChannelId, ChatChannel>()
    private val settingsAtomicRef = AtomicReference(server.globals.chatSettings)
    val settings: EngineChatSettings
        get() = settingsAtomicRef.get()

    fun onSettingsUpdated(settings: EngineChatSettings) {
        settingsAtomicRef.set(settings)
        channelsMap = settings.channels.associateBy { it.id }
    }

    fun getChannel(id: ChannelId): ChatChannel {
        return channelsMap[id] ?: error("Chat channel $id not found")
    }

    fun processMessage(message: IncomingMessage) {
        incomingMessageHistory += message
        coroutineScope.launch {
            val start = System.currentTimeMillis()
            val channel = getChannel(message.channel)
            channel.processMessage(message.content, message.volume, message.source)
            val end = System.currentTimeMillis()
            message.log(end - start)
        }
    }

    fun processMessage(
        channel: ChatChannel,
        source: MessageSource,
        content: String,
        volume: Float = 0.5f
    ) = coroutineScope.launch {
        val start = System.currentTimeMillis()
        channel.processMessage(content, volume, source)
        val end = System.currentTimeMillis()
        logMessage(source, content, end - start)
    }

    fun processMessage(channel: ChatChannel, player: Player, content: String) {
        processMessage(channel, MessageSource.getPlayer(player), content)
    }

    fun processWorldMessage(
        content: String,
        world: World,
        pos: Pos,
        author: String,
        channel: ChatChannel = settings.defaultChannel
    ) {
        processMessage(channel, MessageSource(world, MessageAuthor(author), pos), content)
    }

    fun processSystemMessage(content: String, source: MessageSource) {
        processMessage(ChatChannel.SYSTEM, source, content)
    }

    fun processSystemMessage(content: String, world: World) {
        processMessage(ChatChannel.SYSTEM, MessageSource.getSystem(world), content)
    }

    private suspend fun ChatChannel.processMessage(content: String, volume: Float, source: MessageSource) {
        val sourceWorld = source.world
        val sourcePos = source.position

        val id = MessageId.next()

        // Распространяем сообщение
        // Акустики нет - сообщение нельзя обработать
        val acoustic = acoustic ?: return
        var dontSendMessageToAuthor = false
        val volumes = mutableMapOf<Player, Float>()
        val recipients = mutableListOf<Player>()
        if (sourcePos != null) {
            val toAdd = when (acoustic) {
                is Acoustic.Distance -> {
                    filterNearestPlayers(sourceWorld, sourcePos, acoustic.radius)
                }

                is Acoustic.Realistic -> {
                    val author = source.player
                    val writtenByPlayer = author != null

                    // Посылаем перед симуляцией сообщение автору, чтобы показать иллюзию быстрой обработки
                    dontSendMessageToAuthor = true
                    if (writtenByPlayer) sendMessage(content, source, this, author, volume = volume, id = id)
                    try {
                        runAcousticSpreadSimulation(
                            sourceWorld,
                            sourcePos,
                            volume,
                        ).also {
                            it.forEach { (player, volume) -> volumes[player] = volume }
                        }.keys
                    } catch (e: InvalidMessageSourcePositionException) {
                        if (writtenByPlayer) {
                            server.handler.onServerNotification(author, Notification.INVALID_SOURCE_POS, once = true)
                        }
                        filterNearestPlayers(sourceWorld, sourcePos, 16)
                    }
                }
                else -> { emptyList() }
            }
            recipients.addAll(toAdd)
        }

        if (acoustic == Acoustic.Global) {
            recipients.addAll(sourceWorld.players)
        }

        // Рассылаем сообщение получателям и следящим игрокам, до которых сообщение не дошло
        recipients
            .distinct()
            .filter { it == source.player || it.isChannelAvailableToRead(this) || (mentions && hasMention(it, content)) }
            .forEach {
                if (dontSendMessageToAuthor && it == source.player) return@forEach
                val volume = volumes[it]
                sendMessage(content, source, this, it, volume = volume, id = id)
            }

        players
            .filter { it !in recipients && it.isChatOperator && it != source.player }
            .forEach { sendSpyMessage(content, source, this, it, id = id) }
    }

    fun sendSystemMessage(content: String, recipient: Player) {
        sendMessage(
            content,
            source = MessageSource.getSystem(recipient.world),
            channel = ChatChannel.SYSTEM,
            recipient = recipient,
            id = MessageId.next()
        )
    }

    fun sendMessage(
        content: String,
        source: MessageSource,
        channel: ChatChannel,
        recipient: Player,
        boomerang: Boolean = false,
        volume: Float? = null,
        placeholders: Map<String, String> = mapOf(),
        id: MessageId = MessageId.next()
    ) {
        var content = content
        val mustBeSpectator = channel.modifiers.contains(Modifier.Spectator)
        if (mustBeSpectator && !recipient.isSpectating) return

        val hasMention = channel.notify || hasMention(recipient, content)

        val acoustic = channel.acoustic
        val isRealistic = acoustic is Acoustic.Realistic && volume != null

        if (isRealistic) {
            content = formatTextAcoustic(content, volume, acoustic.distort)
        }

        val isSpeech = channel.speech && source.speech
        val receivers = mutableListOf(recipient)

        val author = source.player
        if (boomerang && author != null) {
            receivers.add(author)
        }

        receivers.forEach { receiver ->
           sendMessageInternal(
               receiver,
               content,
               source,
               channel,
               mention = hasMention,
               speech = isSpeech,
               volume = volume,
               placeholders = getDefaultPlaceholders(recipient, source, volume) + placeholders,
               id = id
           )
        }
    }

    private fun sendSpyMessage(
        content: String,
        source: MessageSource,
        originalChannel: ChatChannel,
        recipient: Player,
        id: MessageId
    ) {
        sendMessageInternal(
            recipient,
            content,
            source,
            originalChannel,
            isSpy = true,
            id = id
        )
    }

    private fun sendMessageInternal(
        recipient: Player,
        text: String,
        source: MessageSource,
        channel: ChatChannel,
        mention: Boolean = hasMention(recipient, text),
        speech: Boolean = false,
        volume: Float? = null,
        isSpy: Boolean = false,
        head: Boolean = showHeads(source.player, channel),
        placeholders: Map<String, String> = getDefaultPlaceholders(recipient, source, volume),
        id: MessageId
    ) {
        server.handler.onOutcomingMessage(
            recipient,
            OutcomingMessage(
                text,
                source,
                channel.id,
                mention,
                speech,
                volume,
                placeholders,
                isSpy,
                head,
                id
            ).also {
                outcomingMessageHistory[it.id] = it
            }
        )
    }

    private fun hasMention(ofPlayer: Player, text: String): Boolean {
        return text.contains("@${ofPlayer.username}")
    }

    private fun showHeads(player: Player?, channel: ChatChannel): Boolean {
        return (player?.chatHeadsEnabled ?: true) && channel.heads
    }

    private fun formatTextAcoustic(
        text: String,
        volume: Float,
        distort: Boolean
    ): String {
        var text = text
        val distortionThreshold = settings.distortionThreshold
        val distortionArtifacts = settings.distortionArtifacts

        if (distort && volume < distortionThreshold) {
            text = text.distort(1f - (volume / distortionThreshold), distortionArtifacts)
        }

        return text
    }

    private fun getDefaultPlaceholders(
        recipient: Player,
        source: MessageSource,
        volume: Float?
    ): Map<String, String> {
        val player = source.player
        val author = source.author
        val placeholders = mutableMapOf(
            "author_username" to (player?.username ?: ""),
            "author_name" to author.name,
            "recipient_username" to recipient.username,
            "recipient_name" to recipient.displayNameMiniMessage,
            "random-100" to roundToInt(Math.random() * 100f).toString()
        )

        volume?.let {
            val level = settings.realisticAcousticFormatting.getLevel(it)
            level.placeholders.forEach { old, new ->
                placeholders[old] = new
            }
        }

        return placeholders
    }

    private suspend fun runAcousticSpreadSimulation(
        world: World,
        pos: Pos,
        volume: Float
    ): Map<Player, Float> {
        val players = world.players
        val acousticLevel = settings.realisticAcousticFormatting.getLevel(volume)

        val results = acousticSimulation.simulateSingleSource(world.id, pos, volume, settings.acousticMaxVolume, acousticLevel.multiplier)

        val recipients = players
            .mapNotNull {
                val pos = it.pos
                val volume = results.getVolume(pos) ?: return@mapNotNull null
                return@mapNotNull if (volume > settings.acousticHearingThreshold) {
                    it to volume
                } else {
                    null
                }
            } // получаем уровни шума
        results.finish()
        return recipients.toMap()
    }

    private fun IncomingMessage.log(time: Long? = null) {
        logMessage(source, content, time)
    }

    private fun logMessage(source: MessageSource, content: String, time: Long? = null) {
        val author = source.player
            ?.let { player ->
                val builder = StringBuilder()
                val displayName = player.displayName
                val username = player.username
                builder.append(displayName)
                if (displayName != username) builder.append(" ($username)")
                builder.toString()
            }
            ?: run {
                val builder = StringBuilder()
                builder.append("Source ")
                builder.append(source.world.id)
                source.position?.let { builder.append(it) }
                builder.toString()
            }
        val time = time?.let { " ($it мл.)" } ?: ""
        CHAT_LOGGER.info("[$author] $content$time")
    }
}
