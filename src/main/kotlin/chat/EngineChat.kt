package org.lain.engine.chat

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.engine.chat.acoustic.AcousticSimulator
import org.lain.engine.chat.acoustic.NEIGHBOURS_26
import org.lain.engine.mc.InvalidMessageSourcePositionException
import org.lain.engine.player.AcousticMessage
import org.lain.engine.player.AcousticMessageQueue
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.acousticDebug
import org.lain.engine.server.EngineServer
import org.lain.engine.server.Notification
import org.lain.engine.util.Color
import org.lain.engine.util.Timestamp
import org.lain.engine.util.component.require
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.roundToInt
import org.lain.engine.world.World
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class EngineChat(
    private val acousticSimulation: AcousticSimulator,
    private val server: EngineServer
) {
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) { r ->
        Thread(r, "Engine Chat Worker").apply { isDaemon = true }
    }
    private val handler = CoroutineExceptionHandler { context, throwable ->
        CHAT_LOGGER.error("При обработке сообщения возникла ошибка", throwable)
    }
    private val coroutineScope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob() + handler)

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
        val acousticDebug = message.source.player?.let { server.playerStorage.get(it.id) }?.acousticDebug ?: false
        val channel = getChannel(message.channel)
        coroutineScope.launch {
            val time = Timestamp()
            channel.processMessage(message.content, message.volume, message.source, acousticDebug)
            message.log(time.timeElapsed())
        }
    }

    fun processMessage(
        channel: ChatChannel,
        source: MessageSource,
        content: String,
        volume: Float = 0.5f,
    ) = coroutineScope.launch {
        val start = System.currentTimeMillis()
        channel.processMessage(content, volume, source, false)
        val end = System.currentTimeMillis()
        logMessage(source, content, end - start)
    }

    private fun filterNearestPlayers(
        world: MessageSource.World,
        pos: Pos,
        radius: Int,
        players: List<MessageSource.Player> = world.players.values.toList(),
    ): List<MessageSource.Player> {
        return players.filter { it.pos.squaredDistanceTo(pos) <= radius*radius }
    }

    suspend fun ChatChannel.processMessage(content: String, volume: Float, source: MessageSource, debugAcoustic: Boolean) {
        val sourceWorld = source.world
        val sourcePos = source.position

        val id = MessageId.next()

        // Распространяем сообщение
        // Акустики нет - сообщение нельзя обработать
        val acoustic = acoustic ?: return
        var dontSendMessageToAuthor = false
        val volumes = mutableMapOf<MessageSource.Player, Volumes>()
        val recipients = mutableListOf<MessageSource.Player>()
        if (sourcePos != null) {
            val toAdd = when (acoustic) {
                is Acoustic.Distance -> {
                    filterNearestPlayers(source.world, sourcePos, acoustic.radius)
                }

                is Acoustic.Realistic -> {
                    val author = source.player
                    val writtenByPlayer = author != null

                    // Посылаем перед симуляцией сообщение автору, чтобы показать иллюзию быстрой обработки
                    dontSendMessageToAuthor = true
                    if (writtenByPlayer) sendMessage(content, source, this, author, volumes = Volumes.fixed(volume), id = id)
                    try {
                        runAcousticSpreadSimulation(
                            author,
                            sourceWorld,
                            sourcePos,
                            volume,
                            debugAcoustic
                        ).also {
                            it.forEach { (player, volume) -> volumes[player] = volume }
                        }.keys
                    } catch (e: InvalidMessageSourcePositionException) {
                        if (writtenByPlayer) {
                            server.handler.onServerNotification(author.id, Notification.INVALID_SOURCE_POS, once = true)
                        }
                        filterNearestPlayers(sourceWorld, sourcePos, 16)
                    }
                }
                else -> { emptyList() }
            }
            recipients.addAll(toAdd)
        }

        if (acoustic == Acoustic.Global) {
            recipients.addAll(sourceWorld.players.values)
        }

        val mustBeSpectator = modifiers.contains(Modifier.Spectator)

        // Рассылаем сообщение получателям и следящим игрокам, до которых сообщение не дошло
        recipients
            .distinct()
            .filter { it == source.player || it.readPermission || (mentions && hasMention(it, content)) && (!mustBeSpectator || it.isSpectating) }
            .forEach {
                if (dontSendMessageToAuthor && it == source.player) return@forEach
                val volume = volumes[it]
                sendMessage(content, source, this, it, volumes = volume, id = id)
            }

        source.world.players.values
            .filter { it !in recipients && it.chatOperator && it != source.player }
            .forEach { sendSpyMessage(content, source, this, it, id = id) }
    }

    fun sendMessage(
        content: String,
        source: MessageSource,
        channel: ChatChannel,
        recipient: MessageSource.Player,
        boomerang: Boolean = false,
        volumes: Volumes? = null,
        placeholders: Map<String, String> = mapOf(),
        id: MessageId = MessageId.next()
    ) {
        var content = content
        val acoustic = channel.acoustic

        if (volumes != null) {
            content = formatTextAcoustic(
                content,
                volumes.result,
                (acoustic as? Acoustic.Realistic)?.distort ?: false
            )
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
                mention = hasMention(recipient, content),
                speech = isSpeech,
                volumes = volumes,
                notify = channel.notify,
                placeholders = getDefaultPlaceholders(recipient, source, volumes) + placeholders,
                background = channel.background,
                id = id
            )
        }
    }

    private fun sendSpyMessage(
        content: String,
        source: MessageSource,
        originalChannel: ChatChannel,
        recipient: MessageSource.Player,
        id: MessageId
    ) {
        sendMessageInternal(
            recipient,
            content,
            source,
            originalChannel,
            notify = originalChannel.notify,
            isSpy = true,
            id = id,
            background = originalChannel.background,
        )
    }

    private fun sendMessageInternal(
        recipient: MessageSource.Player,
        text: String,
        source: MessageSource,
        channel: ChatChannel,
        mention: Boolean = hasMention(recipient, text),
        speech: Boolean = false,
        volumes: Volumes? = null,
        isSpy: Boolean = false,
        notify: Boolean = false,
        head: Boolean = showHeads(source.player, channel),
        placeholders: Map<String, String> = getDefaultPlaceholders(recipient, source, volumes),
        background: Color? = null,
        id: MessageId
    )  {
        val message = OutcomingMessage(
            text,
            source,
            channel.id,
            mention,
            notify,
            speech,
            volumes,
            placeholders,
            isSpy,
            head,
            background,
            id
        )
        server.execute {
            // Два пайплайна. Если сообщение акустическое, обрабатываем его механиками
            if (volumes != null && channel.acoustic is Acoustic.Realistic) {
                val player = server.playerStorage.get(recipient.id)
                player?.require<AcousticMessageQueue>()?.messages += AcousticMessage(message, recipient)
            } else {
                server.handler.onOutcomingMessage(
                    recipient,
                    message
                )
            }
            outcomingMessageHistory[message.id] = message
        }
    }

    private fun hasMention(ofPlayer: MessageSource.Player, text: String): Boolean {
        return text.contains("@${ofPlayer.userName}")
    }

    private fun showHeads(player: MessageSource.Player?, channel: ChatChannel): Boolean {
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
        recipient: MessageSource.Player,
        source: MessageSource,
        volumes: Volumes?
    ): Map<String, String> {
        val player = source.player
        val author = source.author
        val placeholders = mutableMapOf(
            "author_username" to (player?.userName ?: ""),
            "author_name" to author.name,
            "recipient_username" to recipient.userName,
            "recipient_name" to recipient.displayNameMiniMessage,
            "random-100" to roundToInt(Random(source.time.timeMillis).nextFloat() * 100f).toString()
        )

        fun substitute(newPlaceholders: Map<String, String>) {
            newPlaceholders.forEach { (old, new) -> placeholders[old] = new }
        }

        volumes?.input?.let { substitute(settings.realisticAcousticFormatting.getLevel(it).inputPlaceholders) }
        volumes?.result?.let { substitute(settings.realisticAcousticFormatting.getLevel(it).outPlaceholders) }

        return placeholders
    }

    /**
     * @param input Оригинальная громкость сообщения
     * @param result Услышанная громкость
     */
    @Serializable
    data class Volumes(
        val input: Float,
        val result: Float
    ) {
        companion object {
            fun fixed(volume: Float) = Volumes(volume, volume)
        }
    }

    private suspend fun runAcousticSpreadSimulation(
        author: MessageSource.Player?,
        world: MessageSource.World,
        pos: Pos,
        volume: Float,
        debug: Boolean
    ): Map<MessageSource.Player, Volumes> {
        val players = world.players
        val acousticLevel = settings.realisticAcousticFormatting.getLevel(volume)
        val multiplier = acousticLevel.multiplier * settings.acousticAttenuation

        val results = acousticSimulation.simulateSingleSource(world.id, pos, volume, settings.acousticMaxVolume, multiplier) { exception ->
            if (author != null) server.handler.onServerNotification(author.id, Notification.ACOUSTIC_ERROR, false)
            exception.printStackTrace()
        }

        val recipients = players.values
            .mapNotNull {
                val pos = it.eyePos
                val volume =
                    NEIGHBOURS_26
                        .mapNotNull { offset -> results.getVolume(pos.add(offset)) }
                        .maxOrNull()
                        ?: return@mapNotNull null

                return@mapNotNull if (volume > settings.acousticHearingThreshold) {
                    it to volume
                } else {
                    null
                }
            } // получаем уровни шума
            .map { (player, resultVolume) -> player to Volumes(volume, resultVolume) }

        if (debug && author?.id != null) {
            val authorPlayer = server.playerStorage.get(author.id)
            if (authorPlayer != null) {
                results.debug(authorPlayer, server.handler, 16f)
            }
        }

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
                val username = player.userName
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

    fun processMessage(channel: ChatChannel, player: EnginePlayer, content: String) {
        processMessage(channel, MessageSource.getPlayer(player, channel), content)
    }

    fun processWorldMessage(
        content: String,
        world: World,
        pos: Pos,
        author: String,
        channel: ChatChannel = settings.defaultChannel
    ) {
        processMessage(channel, MessageSource.getWorld(world, author, channel), content)
    }

    fun processSystemMessage(content: String, source: MessageSource) {
        processMessage(ChatChannel.SYSTEM, source, content)
    }

    fun processSystemMessage(content: String, world: World) {
        processMessage(ChatChannel.SYSTEM, MessageSource.getSystem(world), content)
    }
}
