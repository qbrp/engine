package org.lain.engine.util.file

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream
import net.minecraft.core.registries.Registries
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.chat.*
import org.lain.engine.mc.*
import org.lain.engine.mc.commands.registerServerChatCommand
import org.lain.engine.player.*
import org.lain.engine.server.ServerGlobals
import org.lain.engine.util.Color
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.jvm.optionals.getOrNull

inline fun <reified T> Yaml.readFile(file: File): T =
    file.inputStream().use { decodeFromStream<T>(it) }

fun loadOrCreateServerConfig(file: File = FileSystem.serverConfig): ServerConfig {
    if (!file.exists()) {
        file.writeText(requireNotNull(FileSystem.builtinResource(FileSystem.SERVER_CONFIG_NAME)).readText())
    }
    return Yaml.default.readFile(file)
}

fun EngineMinecraftServer.applyConfig(config: ServerConfig) {
    val engine = this.engine
    engine.luaScriptEngine.writeCompilationManifest = config.writeCompilationManifest
    val chat = config.chat
    val commandChannels = chat.commands

    val channels: Map<String, ChatChannel> =
        (commandChannels + chat.channels).mapValues { (id, it) ->
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
                        FileSystem.LOGGER.warn("Не указана акустика канала $id. Требуется указать один из вариантов: global, distance или acoustic")
                    }
                    Acoustic.Global
                }

            val modifiers = mutableListOf<Modifier>()
            if (it.spectator) modifiers += Modifier.Spectator

            val selectors = mutableListOf<Selector>()
            it.prefix?.let { value -> selectors += Selector.Prefix(value) }
            it.regex?.let { regex -> selectors += Selector.Regex(regex.exp, regex.remove) }
            val speech = it.speech

            ChatChannel(
                ChannelId(id),
                it.name ?: id,
                format,
                acoustic,
                modifiers,
                selectors,
                speech,
                it.notify,
                it.permission,
                it.heads,
                typeIndicatorRange = it.chatTypeRadius,
                typeIndicator = it.chatTypeIndicator,
                background = it.background?.let { Color.parseString(it) }
            )
        }

    val chatSettings = EngineChatSettings(
        chat.placeholders,
        channels.values.toList(),
        chat.acoustic.distortion.threshold,
        chat.acoustic.distortion.artifacts,
        AcousticFormatting(
            config.chat.acoustic.formatting.map {
                AcousticLevel(
                    it.volume,
                    it.multiplier,
                    it.inputPlaceholders,
                    it.outputPlaceholders,
                )
            }
        ),
        chat.acoustic.volume.hearingThreshold,
        chat.acoustic.volume.max,
        chat.acoustic.volume.attenuation,
        channels[chat.defaultChannel]
            ?: error("Указанный стандартный канал ${chat.defaultChannel} не существует"),
        chat.join.message,
        chat.join.enabled,
        chat.leave.message,
        chat.leave.enabled,
        chat.pm,
    )

    val dispatcher = minecraftServer.commands.dispatcher
    channels.forEach {
        val channel = it.value
        val name = it.key
        val command = commandChannels[name]
        if (command != null) {
            dispatcher.registerServerChatCommand(
                name,
                channel,
                permission = command.invokePermission,
                aliases = command.aliases,
                propagate = command.propagate
            )
        }
    }

    val blockAcousticConfig = chat.acoustic.passability
    val blocks = blockAcousticConfig.blocks.map { (id, value) -> parseId(id) to value }.toMap()
    val tags = blockAcousticConfig.tags.map { (id, value) -> blockTag(id) to value }.toMap()

    // Проверка
    val unidentifiedBlocks = mutableListOf<McIdentifier>()
    blocks.forEach { (blockId, value) ->
        if (registryOf(Registries.BLOCK).getOptional(blockId).getOrNull() == null) {
            unidentifiedBlocks += blockId
        }
    }
    if (unidentifiedBlocks.isNotEmpty()) {
        error("Указана акустика для несуществующих блоков: ${unidentifiedBlocks.joinToString(", ")}")
    }

    acousticSimulator.acousticBlockData.set(
        AcousticBlockData(
            blockAcousticConfig.solid,
            blockAcousticConfig.air,
            blockAcousticConfig.partial,
            blocks,
            tags
        )
    )
    val simulationConfig = chat.acoustic.simulation
    acousticSimulator.range.set(simulationConfig.range)
    acousticSimulator.performanceDebug.set(simulationConfig.performanceDebug)
    acousticSimulator.rebuildDebug = simulationConfig.rebuildDebug

    engine.updateGlobals {
        val volume = config.player.volume
        val vocal = config.vocal
        ServerGlobals(
            it.serverId,
            it.savePath,
            it.playerSynchronizationRadius,
            it.playerDesynchronizationThreshold,
            DefaultPlayerAttributes(
                minVolume = volume.min,
                maxVolume = volume.max,
                baseVolume = volume.base,
                tirednessMultiplier = volume.tirednessMultiplier
            ),
            VocalSettings(
                vocal.breakThreshold,
                vocal.breakChance,
                vocal.regenerationTimeSeconds * 20,
                vocal.regenerationTimeRandom,
                vocal.tirednessThreshold,
                vocal.tirednessGain,
                vocal.tirednessDecreaseRateSeconds / 20
            ),
            chatSettings,
            config.requireIdenticalNamespaces,
            config.player.spectateOnJoin
        )
    }

    ServerMixin.vanillaDamageEnabled = config.player.damage
}

fun EngineMinecraftServer.applyConfigCatching(config: ServerConfig) {
    try {
        applyConfig(config)
    } catch (e: Throwable) {
        FileSystem.LOGGER.error("Возникла ошибка применения конфигурации ${FileSystem.serverConfig.path}", e)
    }
}
