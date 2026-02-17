package org.lain.engine.mc

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.command.argument.Vec3ArgumentType
import net.minecraft.entity.Entity
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.WorldChunk
import org.lain.engine.chat.*
import org.lain.engine.item.EngineSoundCategory
import org.lain.engine.item.ItemId
import org.lain.engine.item.SoundEventId
import org.lain.engine.item.SoundPlay
import org.lain.engine.player.*
import org.lain.engine.server.markDirty
import org.lain.engine.util.*
import org.lain.engine.util.file.applyConfig
import org.lain.engine.util.file.loadContents
import org.lain.engine.util.file.loadOrCreateServerConfig
import org.lain.engine.util.text.displayNameMiniMessage
import org.lain.engine.util.text.parseMiniMessage
import org.lain.engine.world.emitPlaySoundEvent
import org.lain.engine.world.pos
import org.lain.engine.world.world
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

typealias ServerCommandDispatcher = CommandDispatcher<ServerCommandSource>

typealias ServerCommandContext = CommandContext<ServerCommandSource>

private val logger = LoggerFactory.getLogger("Engine Fabric Commands")

fun ServerCommandContext.getPlayers(id: String): List<ServerPlayerEntity> {
    return EntityArgumentType.getPlayers(this, id).toList()
}

fun ServerCommandContext.getPlayerEntity(id: String): ServerPlayerEntity {
    return EntityArgumentType.getPlayer(this, id)
}

fun ServerCommandContext.getPlayer(id: String): EnginePlayer {
    val entityTable by injectEntityTable()
    return entityTable.server.getPlayer(getPlayerEntity(id)) ?: throw FriendlyException("Игрок $id не найден")
}

fun ServerCommandContext.getFloat(id: String): Float {
    return FloatArgumentType.getFloat(this, id)
}

fun ServerCommandContext.getInt(id: String): Int {
    return IntegerArgumentType.getInteger(this, id)
}

fun ServerCommandContext.getString(id: String): String {
    return StringArgumentType.getString(this, id)
}

fun ServerCommandContext.getVec3(id: String): Vec3d {
    return Vec3ArgumentType.getVec3(this, id)
}


fun <T : ArgumentBuilder<ServerCommandSource, T>> ArgumentBuilder<ServerCommandSource, T>.executeCatching(todo: (Context) -> Unit): T {
    val playerTable = injectValue<EntityTable>().server
    return executes {
        val source = it.source
        try {
            val ctx = Context(
                it.source.player?.let { playerTable.getPlayer(it) },
                it.source,
                it
            )
            todo(ctx)
        } catch (e: FriendlyException) {
            source.sendError(e.message!!.parseMiniMessage())
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

private class FriendlyException(message: String) : RuntimeException(message)

fun friendlyError(message: String): Nothing = throw FriendlyException(message)

data class Context(
    val player: EnginePlayer?,
    val source: ServerCommandSource,
    val command: ServerCommandContext
) {
    fun requirePlayer(): EnginePlayer {
        return player ?: throw FriendlyException("Команда предназначена для игрока")
    }

    fun requireEntity(): Entity {
        return source.entity ?: throw FriendlyException("Команда предназначена для сущностей или игроков")
    }

    fun sendFeedback(text: String, broadcastToOps: Boolean) {
        source.sendFeedback({ text.parseMiniMessage() }, broadcastToOps)
    }

    fun sendError(text: String) {
        source.sendError(text.parseMiniMessage())
    }

    fun sendError(exception: Throwable) {
        source.sendError(Text.of(exception.message ?: "При выполнении команды возникла ошибка. Свяжитесь с администратором"))
    }
}

fun ServerCommandDispatcher.registerEngineCommands() {
    val playerTable = injectValue<EntityTable>().server
    val server by injectMinecraftEngineServer()

    register(
        CommandManager.literal("speed")
            .requires { it.hasPermission("attributecommand.speed") }
            .then(
                CommandManager.argument("players", EntityArgumentType.players())
                    .then(
                        CommandManager.literal("reset")
                            .executeCatching { ctx ->
                                val players = ctx.command.getPlayers("players")
                                val playerNameList = players.formatPlayerList()
                                players.forEach { player ->
                                    val enginePlayer = playerTable.requirePlayer(player)
                                    enginePlayer.resetCustomSpeed()
                                }
                                ctx.sendFeedback("Сброшена скорость для игроков $playerNameList", true)
                            }
                    )
                    .then(
                        CommandManager.literal("set")
                            .then(
                                CommandManager.argument("value", FloatArgumentType.floatArg())
                                    .executeCatching { ctx ->
                                        val players = ctx.command.getPlayers("players")
                                        val speed = ctx.command.getFloat("value")
                                        val playerNameList = players.formatPlayerList()
                                        players.forEach { player ->
                                            val enginePlayer = playerTable.requirePlayer(player)
                                            enginePlayer.setCustomSpeed(speed)
                                        }
                                        ctx.sendFeedback("Установлена скорость $speed для игроков $playerNameList", true)
                                    }
                            )
                    )
            )
    )

    register(
        CommandManager.literal("jumpstrength")
            .requires { it.hasPermission("attributecommand.jumpstrength") }
            .then(
                CommandManager.argument("players", EntityArgumentType.players())
                    .then(
                        CommandManager.literal("set")
                            .then(
                                CommandManager.argument("value", FloatArgumentType.floatArg())
                                    .executeCatching { ctx ->
                                        val players = ctx.command.getPlayers("players")
                                        val speed = ctx.command.getFloat("value")
                                        val playerNameList = players.formatPlayerList()
                                        players.forEach { player ->
                                            val enginePlayer = playerTable.requirePlayer(player)
                                            enginePlayer.setCustomJumpStrength(speed)
                                        }
                                        ctx.sendFeedback("Установлена сила прыжка $speed для игроков $playerNameList", true)
                                    }
                            )
                    )
                    .then(
                        CommandManager.literal("reset")
                            .executeCatching { ctx ->
                                val players = ctx.command.getPlayers("players")
                                val playerNameList = players.formatPlayerList()
                                players.forEach { player ->
                                    val enginePlayer = playerTable.requirePlayer(player)
                                    enginePlayer.resetCustomJumpStrength()
                                }
                                ctx.sendFeedback("Сброшена сила прыжка для игроков $playerNameList", true)
                            }
                    )
            )
    )

    register(
        CommandManager.literal("setname")
            .then(
                CommandManager.argument("args", StringArgumentType.greedyString())
                    .executeCatching { ctx ->
                        val raw = ctx.command.getString("args")
                        val player = ctx.requirePlayer()

                        val parts = raw.split(" ")
                        if (parts.isEmpty()) friendlyError("Использование: /setname <имя> <цвет1> [цвет2]")

                        val name = parts[0]
                        val color1 = parts.getOrNull(1)?.replace("#", "")
                        val color2 = parts.getOrNull(2)?.replace("#", "")

                        try {
                            player.customName = CustomName(
                                name,
                                color1?.let { Color.parseString(it) } ?: Color.WHITE,
                                color2?.let { Color.parseString(it) }
                            )
                            player.markDirty<DisplayName>()
                        } catch (e: InvalidCustomNameException) {
                            ctx.sendError(e)
                        }

                        ctx.sendFeedback("Установлено имя ${player.displayNameMiniMessage}", false)
                    }
            )
    )


    register(
        CommandManager.literal("spawn")
            .executeCatching { ctx ->
                val player = ctx.requirePlayer()
                player.stopSpectating()
            }
    )

    register(
        CommandManager.literal("reloadengineconfig")
            .requires { it.hasPermission("reloadengineconfig") }
            .executeCatching { ctx ->
                try {
                    server.applyConfig(loadOrCreateServerConfig())
                    ctx.sendFeedback("Конфигурация перезагружена", true)
                } catch (e: Exception) {
                    ctx.sendError("Возникла ошибка при применении конфигурации: ${e.message ?: "Unknown"}")
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
                                                val text = ctx.command.getString("text")
                                                val author = ctx.command.getString("author")
                                                val volume = ctx.command.getFloat("volume")
                                                val pos = Vec3ArgumentType.getPosArgument(ctx.command, "pos").getPos(source)
                                                val chat = engine.chat

                                                val message = IncomingMessage(
                                                    text,
                                                    volume,
                                                    chat.settings.defaultChannel.id,
                                                    MessageSource(
                                                        world,
                                                        MessageAuthor(author),
                                                        Timestamp(),
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
                                val text = ctx.command.getString("text")
                                val entity = ctx.command.getPlayerEntity("player")
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
                        CommandManager.literal("status")
                            .executeCatching { ctx ->
                                val player = ctx.command.getPlayer("player")
                                val voiceLoose = player.get<VoiceLoose>()

                                if (voiceLoose == null) {
                                    ctx.sendFeedback("Голос игрока не сорван.", false)
                                } else {
                                    val regenerationTimeSeconds = voiceLoose.ticksToRegeneration / 20
                                    val regenerationTimeMinutes = regenerationTimeSeconds / 60
                                    val timeElapsedSeconds = voiceLoose.ticks / 20
                                    ctx.sendFeedback(
                                        "Голос сорван на $regenerationTimeSeconds секунд (~$regenerationTimeMinutes минут).<newline>"
                                        + "Прошло $timeElapsedSeconds секунд.",
                                        false
                                    )
                                }
                            }
                    )
                    .then(
                        CommandManager.literal("remove")
                            .executeCatching { ctx ->
                                val player = ctx.command.getPlayer("player")

                                player.apply<VoiceApparatus> { tiredness = 0f }
                                player.remove<VoiceLoose>()?.let { ctx.sendFeedback("Голос игрока ${player.username} восстановлен", true) }
                            }
                    )
        )
    )

    register(
        CommandManager.literal("engineitem")
            .requires { it.hasPermission("engineitem") }
            .then(
                CommandManager.argument("id", StringArgumentType.string())
                    .suggests(
                        NamespacedIdProvider { it.itemIdentifiers }
                    )
                    .executeCatching { ctx ->
                        val argument = ctx.command.getString("id")
                        val id = ItemId(argument)
                        val player = ctx.source.player ?: friendlyError("Команда доступна только игроку")

                        val storage = server.engine.namespacedStorage
                        val items = mutableListOf<ItemId>()
                        val namespace = storage.namespaces[NamespaceId(id.toString())]
                        if (namespace != null) {
                            items += namespace.items.keys.toList()
                        } else {
                            items += id
                        }

                        val prefabs = items.mapNotNull { storage.items[it] }
                        if (prefabs.isEmpty()) {
                            friendlyError("Предметы по идентификатору $id не найдены")
                        }

                        prefabs.forEach { prefab ->
                            server.createItemStack(ctx.requirePlayer(), prefab.id) { itemStack, item ->
                                val copy = itemStack.copy()
                                player.giveItemStack(copy)
                                copy
                            }
                        }

                        val format = if (prefabs.count() == 1) {
                            "Выдан предмет %s игроку %s"
                        } else {
                            "Выданы предметы %s игроку %s"
                        }

                        val text = String.format(format, argument, player.displayName?.string)
                        ctx.source.sendFeedback({ Text.of(text) }, true)
                    }
            )
    )

    register(
        CommandManager.literal("reloadenginecontents")
            .requires { it.hasPermission("reloadenginecontents") }
            .executeCatching {
                server.loadContents()
                it.sendFeedback("Контент перезагружен", true)
            }
    )

    fun executeEngineSoundCommand(ctx: Context, id: String, pos: Vec3d? = null, volume: Float = 1f) {
        val storage = server.engine.namespacedStorage
        val id = SoundEventId(id)
        val player = ctx.requirePlayer()
        val event = storage.sounds[id] ?: friendlyError("Звуковое событие по идентификатору $id не найдено")
        val pos = pos?.engine() ?: player.pos.copy()

        player.world.emitPlaySoundEvent(
            SoundPlay(
                event,
                pos,
                EngineSoundCategory.PLAYERS,
                volume
            )
        )

        ctx.sendFeedback("Воспроизведено звуковое событие ${event.id} на координатах ${pos.x}, ${pos.y}, ${pos.z} громкостью $volume", true)
    }

    register(
        CommandManager.literal("enginesound")
            .requires { it.hasPermission("enginesound") }
            .then(
                CommandManager.argument("id", StringArgumentType.string())
                    .suggests(
                        NamespacedIdProvider { it.soundIdentifiers }
                    )
                    .executeCatching { ctx ->
                        val argument = ctx.command.getString("id")
                        executeEngineSoundCommand(ctx, argument)
                    }
                    .then(
                        CommandManager.argument("pos", Vec3ArgumentType.vec3())
                            .executeCatching { ctx ->
                                val argument = ctx.command.getString("id")
                                val pos = ctx.command.getVec3("pos")
                                executeEngineSoundCommand(ctx, argument, pos)
                            }
                            .then(
                                CommandManager.argument("volume", FloatArgumentType.floatArg())
                                    .executeCatching { ctx ->
                                        val argument = ctx.command.getString("id")
                                        val pos = ctx.command.getVec3("pos")
                                        val volume = ctx.command.getFloat("volume")
                                        executeEngineSoundCommand(ctx, argument, pos, volume)
                                    }
                            )
                    )
            )
    )

    register(
        CommandManager.literal("chatheads")
            .requires { it.hasPermission(CHAT_HEADS_PERMISSION) }
            .executeCatching {
                val player = it.requirePlayer()
                val enabled = player.toggleChatHeads()
                it.sendFeedback("Отображение иконки персонажа ${if (enabled) "включено" else "отключено"}", false)
            }
    )

    register(
        CommandManager.literal("engineticks")
            .requires { it.hasPermission("engineticks") }
            .executeCatching {
                val stats = getServerStats(server.engine.tickTimes.toList())
                it.sendFeedback("Средняя длительность последних 20 тактов engine: ${stats.averageTickTimeMillis} мл.", false)
            }
    )

    fun getLookedBlock(entity: Entity): Pair<BlockPos, WorldChunk> {
        val lookPos = entity.raycast(10.0, 0.0f, false).pos
        val blockPos = BlockPos.ofFloored(lookPos)
        val chunkPos = entity.chunkPos
        val chunk = entity.entityWorld.getChunk(chunkPos.x, chunkPos.z)
        return blockPos to chunk
    }

    register(
        CommandManager.literal("sethint")
            .requires { it.hasPermission("sethint") }
            .then(
                CommandManager.argument("text", StringArgumentType.greedyString())
                    .executeCatching { ctx ->
                        val entity = ctx.requireEntity()
                        val (blockPos, chunk) = getLookedBlock(entity)
                        val text = ctx.command.getString("text")
                        val number = text.split(" ").lastOrNull()?.let {
                            if (it.all { char -> char.isDigit() }) it else null
                        }?.toInt()

                        val hint = chunk.setBlockHint(blockPos, text, number)
                        ctx.sendFeedback(hint.displayText(hint.texts.indexOf(text)), true)
                    }
            )
    )

    register(
        CommandManager.literal("detachhint")
            .requires { it.hasPermission("detachhint") }
            .then(
                CommandManager.argument("index", IntegerArgumentType.integer())
                    .executeCatching { ctx ->
                        val entity = ctx.requireEntity()
                        val (blockPos, chunk) = getLookedBlock(entity)
                        val index = ctx.command.getInt("index")

                        chunk.detachBlockHint(blockPos, index)
                        ctx.sendFeedback("Удален текст под индексом $index", true)
                    }
            )
    )

    registerServerPmCommand()
}

class NamespacedIdProvider(
    val provider: (NamespacedStorage) -> List<String>,
) : SuggestionProvider<ServerCommandSource> {
    private val server by injectEngineServer()
    private val storage get() = server.namespacedStorage

    override fun getSuggestions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remainingLowerCase.replace(""""""", "")
        val identifiers = provider(storage)
        identifiers
            .filter {
                it.startsWith(input) || it.split("/").any { it.startsWith(input) }
            }
            .forEach { builder.suggest('"' + it + '"') }
        return builder.buildFuture()
    }
}

/**
 * Регистрация команды типа `/<название> <содержание>`, отправляющий содержание в чат-каналs
 * @param name Название команды (/команда)
 * @param channel В какой канал будет отправляться `содержание`
 * @param argument Название аргумента команды
 */
fun ServerCommandDispatcher.registerServerChatCommand(name: String, channel: ChatChannel, argument: String = "text", permission: Boolean = false) {
    val engine by injectEngineServer()
    register(
        CommandManager.literal(name)
            .requires { !permission || it.player?.hasPermission("chat.$name") == true }
            .then(
                CommandManager.argument(argument, StringArgumentType.greedyString())
                    .executeCatching { ctx ->
                        val text = ctx.command.getString(argument)
                        val player = ctx.requirePlayer()
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
                                val text = ctx.command.getString("text")
                                val recipient = ctx.command.getPlayerEntity("player")
                                val authorPlayer = ctx.requirePlayer()
                                val recipientPlayer = table.server.requirePlayer(recipient)

                                if (recipientPlayer == authorPlayer && !authorPlayer.developerMode) {
                                    ctx.source.sendError(Text.of("Вы не можете написать самому себе"))
                                    return@executeCatching
                                }

                                engine.chat.sendMessage(
                                    text,
                                    MessageSource.getPlayer(authorPlayer),
                                    engine.chat.settings.pmChannel,
                                    recipientPlayer,
                                    boomerang = true,
                                    placeholders = mapOf(
                                        "pm_receiver_username" to recipientPlayer.username,
                                        "pm_receiver_name" to recipientPlayer.displayNameMiniMessage
                                    )
                                )
                            }
                    )
            )
    )
}