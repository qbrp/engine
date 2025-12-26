package org.lain.engine.mc

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.chat.MessageAuthor
import org.lain.engine.chat.MessageSource
import org.lain.engine.player.VoiceApparatus
import org.lain.engine.player.VoiceLoose
import org.lain.engine.player.customName
import org.lain.engine.player.taskCommand
import org.lain.engine.player.resetCustomSpeed
import org.lain.engine.player.resetCustomJumpStrength
import org.lain.engine.player.setCustomSpeed
import org.lain.engine.player.setCustomJumpStrength
import org.lain.engine.player.speak
import org.lain.engine.player.stopSpectating
import org.lain.engine.player.volume
import org.lain.engine.util.apply
import org.lain.engine.util.applyConfig
import org.lain.engine.util.compileItems
import org.lain.engine.util.get
import org.lain.engine.util.injectEngineServer
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.injectItemContext
import org.lain.engine.util.loadOrCreateServerConfig
import org.lain.engine.util.parseMiniMessage
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

typealias ServerCommandDispatcher = CommandDispatcher<ServerCommandSource>

typealias ServerCommandContext = CommandContext<ServerCommandSource>

private val logger = LoggerFactory.getLogger("Engine Fabric Commands")

fun ServerCommandContext.getPlayers(id: String): List<ServerPlayerEntity> {
    return EntityArgumentType.getPlayers(this, id).toList()
}

fun ServerCommandContext.getPlayer(id: String): ServerPlayerEntity {
    return EntityArgumentType.getPlayer(this, id)
}

fun ServerCommandContext.getFloat(id: String): Float {
    return FloatArgumentType.getFloat(this, id)
}

fun ServerCommandContext.getString(id: String): String {
    return StringArgumentType.getString(this, id)
}

fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeCatching(todo: (ServerCommandContext) -> Unit): T {
    return executes {
        val source = it.source
        try {
            todo(it)
        } catch (e: Throwable) {
            source.sendError(Text.of { e.message ?: "Неизвестная ошибка" })
            logger.error("Возникла ошибка при выполнении команды ${e.message}", e)
        }
        1
    }
}

fun ServerCommandSource.hasPermission(text: String): Boolean {
    if (hasPermissionLevel(4)) return true
    if (player?.hasPermission(text) == true) return true
    return false
}

fun List<ServerPlayerEntity>.formatPlayerList() = joinToString(separator = ", ") { it.name.string }

fun ServerCommandDispatcher.registerEngineCommands() {
    val playerTable by injectEntityTable()
    val server by injectMinecraftEngineServer()

    register(
        CommandManager.literal("speed")
            .requires { it.hasPermission("attributecommand.speed") }
            .then(
                CommandManager.argument("players", EntityArgumentType.players())
                    .then(
                        CommandManager.literal("resetcustom")
                    ).executeCatching { ctx ->
                        val source = ctx.source
                        val players = ctx.getPlayers("players")
                        val playerNameList = players.formatPlayerList()
                        players.forEach { player ->
                            val enginePlayer = playerTable.requirePlayer(player)
                            enginePlayer.taskCommand {
                                enginePlayer.resetCustomSpeed()
                                source.sendFeedback(
                                    { Text.of("Сброшена скорость для игроков $playerNameList") },
                                    true
                                )
                            }
                        }
                    }
                    .then(
                        CommandManager.argument("value", FloatArgumentType.floatArg())
                            .executeCatching { ctx ->
                                val source = ctx.source
                                val players = ctx.getPlayers("players")
                                val playerNameList = players.formatPlayerList()
                                players.forEach { player ->
                                    val enginePlayer = playerTable.requirePlayer(player)
                                    val speed = ctx.getFloat("value")
                                    enginePlayer.taskCommand {
                                        enginePlayer.setCustomSpeed(speed)
                                        source.sendFeedback(
                                            { Text.of("Установлена скорость $speed для игроков $playerNameList") },
                                            true
                                        )
                                    }
                                }
                            }
                    )
            )
    )

    register(
        CommandManager.literal("jumpstrength")
            .requires { it.hasPermission("attributecommand.jumpstrength") }
            .then(
                CommandManager.argument("players", EntityArgumentType.players())
                    .then(
                        CommandManager.literal("resetcustom")
                    ).executeCatching { ctx ->
                        val source = ctx.source
                        val players = ctx.getPlayers("players")
                        val playerNameList = players.formatPlayerList()
                        players.forEach { player ->
                            val enginePlayer = playerTable.requirePlayer(player)
                            enginePlayer.taskCommand {
                                enginePlayer.resetCustomJumpStrength()
                                source.sendFeedback(
                                    { Text.of("Сброшена сила прыжка для игроков $playerNameList") },
                                    true
                                )
                            }
                        }
                    }
                    .then(
                        CommandManager.argument("value", FloatArgumentType.floatArg())
                            .executeCatching { ctx ->
                                val source = ctx.source
                                val players = ctx.getPlayers("players")
                                val playerNameList = players.formatPlayerList()
                                players.forEach { player ->
                                    val enginePlayer = playerTable.requirePlayer(player)
                                    enginePlayer.taskCommand {
                                        val speed = ctx.getFloat("value")
                                        enginePlayer.setCustomJumpStrength(speed)
                                        source.sendFeedback(
                                            { Text.of("Установлена сила прыжка $speed для игроков $playerNameList") },
                                            true
                                        )
                                    }
                                }
                            }
                    )
            )
    )

    register(
        CommandManager.literal("setname")
            .then(
                CommandManager.argument("value", StringArgumentType.greedyString())
                    .executeCatching { ctx ->
                        val name = ctx.getString("value")
                        val source = ctx.source
                        val entity = ctx.source.player
                        if (entity == null) {
                            source.sendError(Text.of("Команда доступна только игроку"))
                        } else {
                            val player = playerTable.requirePlayer(entity)
                            player.taskCommand {
                                player.customName = name
                                source.sendFeedback({ Text.literal("Установлено имя ").append(name.parseMiniMessage()) }, false)
                            }
                        }
                    }
            )
    )

    register(
        CommandManager.literal("spawn")
            .executeCatching { ctx ->
                val entity = ctx.source.player ?: error("Команда предназначена для игроков")
                val player = playerTable.requirePlayer(entity)
                player.taskCommand { player.stopSpectating() }
            }
    )

    register(
        CommandManager.literal("reloadengineconfig")
            .requires { it.hasPermission("reloadengineconfig") }
            .executeCatching { ctx ->
                val source = ctx.source
                try {
                    server.applyConfig(loadOrCreateServerConfig())
                    source.sendFeedback({ Text.of("Конфигурация перезагружена") }, true)
                } catch (e: Exception) {
                    source.sendError(Text.of("Возникла ошибка при применении конфигурации: ${e.message ?: "Unknown"}"))
                }
            }
    )

    register(
        CommandManager.literal("sourcemessage")
            .requires { it.hasPermission("sourcemessage") }
            .then(
                CommandManager.argument("pos", Vec3ArgumentType.vec3())
                    .then(
                        CommandManager.argument("author", StringArgumentType.string()
                        )
                            .then(
                                CommandManager.argument("volume", FloatArgumentType.floatArg())
                                    .then(
                                        CommandManager.argument("text", StringArgumentType.greedyString())
                                            .executeCatching { ctx ->
                                                val source = ctx.source
                                                val engine = server.engine
                                                val world = engine.getWorld(source.world.engine)
                                                val text = ctx.getString("text")
                                                val author = ctx.getString("author")
                                                val volume = ctx.getFloat("volume")
                                                val pos = Vec3ArgumentType.getPosArgument(ctx, "pos").getPos(source)
                                                val chat = engine.chat

                                                val message = IncomingMessage(
                                                    text,
                                                    volume,
                                                    chat.settings.defaultChannel.id,
                                                    MessageSource(
                                                        world,
                                                        MessageAuthor(author),
                                                        pos.engine()
                                                    )
                                                )

                                                chat.processMessage(message)
                                            }
                                    )
                            )
                    )
            )
    )

    register(
        CommandManager.literal("speak")
            .requires { it.hasPermission("speak") }
            .then(
                CommandManager.argument("player", EntityArgumentType.player(),
                )
                    .then(
                        CommandManager.argument("text", StringArgumentType.greedyString())
                            .executeCatching { ctx ->
                                val text = ctx.getString("text")
                                val entity = EntityArgumentType.getPlayer(ctx, "player")
                                val player = playerTable.requirePlayer(entity)

                                player.speak(text)
                            }
                    )
            )
    )

    register(
        CommandManager.literal("voicebreak")
            .requires { it.hasPermission("voicebreak") }
            .then(
                CommandManager.argument("player", EntityArgumentType.player())
                    .then(
                        CommandManager.literal("remove")
                            .executeCatching { ctx ->
                                val entity = EntityArgumentType.getPlayer(ctx, "player")
                                val player = playerTable.requirePlayer(entity)

                                player.apply<VoiceApparatus> {
                                    tiredness = 0f
                                }
                                player.remove<VoiceLoose>()?.let {
                                    ctx.source.sendFeedback({ Text.of("Голос игрока ${entity.name.string} восстановлен") }, true)
                                }
                            }
                    )
                    .then(
                        CommandManager.literal("status")
                            .executeCatching { ctx ->
                                val entity = EntityArgumentType.getPlayer(ctx, "player")
                                val player = playerTable.requirePlayer(entity)
                                val voiceLoose = player.get<VoiceLoose>()

                                if (voiceLoose == null) {
                                    ctx.source.sendFeedback({ Text.of("Голос игрока не сорван.") }, false)
                                } else {
                                    val regenerationTimeSeconds = voiceLoose.ticksToRegeneration / 20
                                    val regenerationTimeMinutes = regenerationTimeSeconds / 60
                                    val timeElapsedSeconds = voiceLoose.ticks / 20
                                    ctx.source.sendFeedback(
                                        {
                                            Text.empty()
                                                .append("Голос сорван на $regenerationTimeSeconds секунд (~$regenerationTimeMinutes минут). ")
                                                .append("Прошло $timeElapsedSeconds секунд.")
                                        },
                                        false
                                    )
                                }
                            }
                    )
        )
    )

    register(
        CommandManager.literal("engineitem")
            .requires { it.hasPermission("engineitem") }
            .then(
                CommandManager.argument("item", StringArgumentType.string())
                    .suggests(EngineItemsSuggestionProvider)
                    .executeCatching { ctx ->
                        val itemContext by injectItemContext()
                        val itemId = ItemId(ctx.getString("item"))
                        val player = ctx.source.player ?: error("Команда доступна только игроку")
                        val itemStack = itemContext.itemRegistry.stacks[itemId] ?: error("Предмет $itemId не существует")
                        player.giveItemStack(itemStack.copy())
                        ctx.source.sendFeedback({ Text.of("Выдан предмет $itemId игроку ${player.displayName?.string}") }, true)
                    }
            )
    )

    register(
        CommandManager.literal("reloadengineitems")
            .requires { it.hasPermission("reloadengineitems") }
            .executeCatching {
                server.compileItems()
                it.source.sendFeedback({ Text.of("Предметы перезагружены") }, true)
            }
    )

    registerServerPmCommand()
}

object EngineItemsSuggestionProvider : SuggestionProvider<ServerCommandSource> {
    private val itemContext by injectItemContext()
    override fun getSuggestions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        itemContext.itemRegistry.stacks.keys.forEach {
            builder.suggest('"' + it.value + '"')
        }
        return builder.buildFuture()
    }
}

/**
 * Регистрация команды типа `/<название> <содержание>`, отправляющий содержание в чат-канал
 * @param name Название команды (/команда)
 * @param channel В какой канал будет отправляться `содержание`
 * @param argument Название аргумента команды
 */
fun ServerCommandDispatcher.registerServerChatCommand(name: String, channel: ChatChannel, argument: String = "text") {
    val engine by injectEngineServer()
    val table by injectEntityTable()
    register(
        CommandManager.literal(name)
            .then(
                CommandManager.argument(argument, StringArgumentType.greedyString())
                    .executeCatching { ctx ->
                        val text = ctx.getString(argument)
                        val entity = ctx.source.player ?: throw IllegalArgumentException("Команда предназначена для игроков")
                        val player = table.requirePlayer(entity)
                        engine.chat.processMessage(channel, MessageSource.getPlayer(player), text)
                    }
            )
    )
}

fun ServerCommandDispatcher.registerServerPmCommand() {
    val engine by injectEngineServer()
    val table by injectEntityTable()

    register(
        CommandManager.literal("pm")
            .then(
                CommandManager.argument("player", EntityArgumentType.player())
                    .then(
                        CommandManager.argument("text", StringArgumentType.greedyString())
                            .executeCatching { ctx ->
                                val text = ctx.getString("text")
                                val recipient = ctx.getPlayer("player")
                                val authorEntity = ctx.source.player ?: throw IllegalArgumentException("Команда предназначена для игроков")
                                val authorPlayer = table.requirePlayer(authorEntity)
                                val recipientPlayer = table.requirePlayer(recipient)

                                val pmChannel = ChatChannel(
                                    ChannelId("pm"),
                                    "Личные сообщения",
                                    engine.chat.settings.pmFormat,
                                    notify = true
                                )

                                engine.chat.sendMessage(
                                    text,
                                    MessageSource.getPlayer(authorPlayer),
                                    pmChannel,
                                    recipientPlayer,
                                    boomerang = true
                                )
                            }
                    )
            )
    )
}