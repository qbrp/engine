package org.lain.engine.util

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.util.Identifier
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.chat.Acoustic
import org.lain.engine.chat.AcousticFormatting
import org.lain.engine.chat.AcousticLevel
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.Modifier
import org.lain.engine.chat.Selector
import org.lain.engine.mc.AcousticBlockData
import org.lain.engine.mc.ServerMixinAccess
import org.lain.engine.mc.registerServerChatCommand
import org.lain.engine.player.MovementDefaultAttributes
import org.lain.engine.player.PlayerStatus
import org.lain.engine.player.PrimaryAttribute
import org.lain.engine.player.VocalSettings
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.forEach
import kotlin.collections.get

private val CONFIG_FILE = ENGINE_DIR.resolve(CONFIG_FILENAME)
private const val CONFIG_FILENAME = "server-config.yml"
internal val CONFIG_LOGGER = LoggerFactory.getLogger("Engine Config")

fun loadOrCreateServerConfig(
    file: File = CONFIG_FILE,
    defaultResourceConfig: String = CONFIG_FILENAME
): ServerConfig {
    if (!file.exists()) {
        val classLoader = Thread.currentThread().contextClassLoader
        val resource = classLoader.getResource(defaultResourceConfig)
            ?: error("Не найден ресурс стандартного конфига: $defaultResourceConfig")
        val text = resource.readText()
        file.writeText(text)
    }
    return Yaml.default.decodeFromStream(file.inputStream())
}

fun EngineMinecraftServer.applyConfig(config: ServerConfig) {
    val engine = this.engine
    val chat = config.chat
    val commandChannels = chat.commands

    val channels: Map<String, ChatChannel> = (chat.channels + commandChannels).mapValues { (id, it) ->
        val format = it.format
        val acoustic = it.distance
            ?.let { value ->
                Acoustic.Distance(value)
            }
            ?: it.acoustic?.let { acoustic ->
                Acoustic.Realistic(acoustic.distort)
            }
            ?: it.global.let { isGlobal ->
                if (!isGlobal) {
                    CONFIG_LOGGER.warn("Не указана акустика канала $id. Требуется указать один из вариантов: global, distance или acoustic")
                }
                Acoustic.Global
            }

        val modifiers = mutableListOf<Modifier>()
        if (it.spectator) modifiers += Modifier.Spectator

        val selectors = mutableListOf<Selector>()
        it.prefix?.let { value -> selectors += Selector.Prefix(value) }
        it.regex?.let { regex -> selectors += Selector.Regex(regex.exp, regex.remove) }
        val speech = it.speech

        ChatChannel(ChannelId(id), it.name ?: id, format, acoustic, modifiers, selectors, speech, it.notify, it.permission)
    }

    this.engine.chat.updateSettings(
        EngineChatSettings(
            chat.placeholders,
            channels.values.toList(),
            chat.acoustic.distortion.threshold,
            chat.acoustic.distortion.artifacts,
            AcousticFormatting(
                config.chat.acoustic.formatting.map {
                    AcousticLevel(
                        it.volume,
                        it.placeholders
                    )
                }
            ),
            chat.acoustic.volume.hearingThreshold,
            chat.acoustic.volume.max,
            channels[chat.defaultChannel] ?: error("Указанный стандартный канал ${chat.defaultChannel} не существует"),
            chat.join.message,
            chat.join.enabled,
            chat.leave.message,
            chat.leave.enabled,
            chat.pm,
        )
    )

    val dispatcher = minecraftServer.commandManager.dispatcher
    channels.forEach {
        val channel = it.value
        val name = it.key
        if (!commandChannels.contains(name)) return@forEach
        dispatcher.registerServerChatCommand(name, channel)
    }

    val blockAcousticConfig = chat.acoustic.passability
    val blocks = blockAcousticConfig.blocks.map { (id, value) -> Identifier.of(id) to value }.toMap()
    val tags = blockAcousticConfig.tags.map { (id, value) -> TagKey.of(RegistryKeys.BLOCK, Identifier.of(id)) to value }.toMap()
    acousticSimulator.acousticBlockData.set(
        AcousticBlockData(
            blockAcousticConfig.solid,
            blockAcousticConfig.air,
            blocks,
            tags
        )
    )
    val simulationConfig = chat.acoustic.simulation
    acousticSimulator.steps.set(simulationConfig.steps)
    acousticSimulator.range.set(simulationConfig.range)
    acousticSimulator.chunkSize.set(simulationConfig.chunkSize)
    acousticSimulator.performanceDebug.set(simulationConfig.performanceDebug)

    val statuses = mutableMapOf<PlayerStatus, Map<PrimaryAttribute, Float>>()
    config.player.attributes.forEach { (status, value) ->
        val playerStatus = when(status) {
            "default" -> PlayerStatus.DEFAULT
            "spectator" -> PlayerStatus.SPECTATING
            "gm" -> PlayerStatus.GM
            else -> throw IllegalArgumentException("Статус $status не существует. Доступны: default, spectator, gm")
        }

        val attributes = value.mapKeys {
            val attribute = when (it.key) {
                "speed" -> PrimaryAttribute.SPEED
                "jump-strength" -> PrimaryAttribute.JUMP_STRENGTH
                else -> throw IllegalArgumentException("Атрибут ${it.key} не существует. Доступны: speed, jump-strength")
            }
            attribute
        }

        statuses[playerStatus] = attributes
    }
    val volume = config.player.volume
    engine.updateDefaultPlayerAttributes {
        it.minVolume = volume.min
        it.maxVolume = volume.max
        it.baseVolume = volume.base
        it.movement = MovementDefaultAttributes(statuses)
    }

    val vocal = config.vocal
    engine.globals.vocalSettings = VocalSettings(
        vocal.breakThreshold,
        vocal.breakChance,
        vocal.regenerationTimeSeconds * 20,
        vocal.regenerationTimeRandom,
        vocal.tirednessThreshold,
        vocal.tirednessGain,
        vocal.tirednessDecreaseRateSeconds / 20
    )

    ServerMixinAccess.isDamageEnabled = config.player.damage
}

fun EngineMinecraftServer.applyConfigCatching(config: ServerConfig) {
    try {
        applyConfig(config)
    } catch (e: Throwable) {
        CONFIG_LOGGER.error("Возникла ошибка применения конфигурации ${CONFIG_FILE.path}", e)
    }
}