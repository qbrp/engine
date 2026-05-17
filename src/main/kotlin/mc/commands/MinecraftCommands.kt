package org.lain.engine.mc.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import org.lain.cyberia.ecs.*
import org.lain.engine.chat.CHAT_HEADS_PERMISSION
import org.lain.engine.chat.IncomingMessage
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.chatChannelOf
import org.lain.engine.item.ItemId
import org.lain.engine.mc.*
import org.lain.engine.player.*
import org.lain.engine.script.*
import org.lain.engine.server.markDirty
import org.lain.engine.transport.packet.ClientChatChannel
import org.lain.engine.transport.packet.ClientChatSettings
import org.lain.engine.util.*
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.world.*
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

fun World.updateCommandInvokeSystem(table: EntityTable) {
    val mcWorld = table.getMcWorld(id) as? ServerLevel ?: error("World $id not found")
    val server = mcWorld.server ?: error("Minecraft server is not available")
    val commandDispatcher = server.commands
    iterate<Event, CommandInvoke>() { _, _, (command) ->
        commandDispatcher.performPrefixedCommand(server.createCommandSourceStack(), command)
    }
    iterate<PlayerCommandAccess, CommandInvoke>() { componentAccess, (player, root), (command) ->
        val mcPlayer = table.server.getEntity(player) ?: error("Player $player not found")
        var commandSourceStack = mcPlayer.createCommandSourceStack()
        if (root) commandSourceStack = commandSourceStack.withPermission(LevelBasedPermissionSet.OWNER)
        commandDispatcher.performPrefixedCommand(
            commandSourceStack,
            command
        )
    }
}

fun playerPositionsMessage(playerStorage: PlayerStorage, world: Level): List<String> {
    val enginePlayers = playerStorage.getAll()
    val minecraftPlayers = world.players().toList()
    return minecraftPlayers.mapNotNull { mcPlayer ->
        val enginePlayer = enginePlayers.find { it.id == mcPlayer.engineId } ?: return@mapNotNull null
        val enginePosFormatted = "%.2f, %.2f, %.2f".format(
            enginePlayer.pos.x,
            enginePlayer.pos.y,
            enginePlayer.pos.z
        )

        val minecraftPosFormatted = "%.2f, %.2f, %.2f".format(
            mcPlayer.position().x,
            mcPlayer.position().y,
            mcPlayer.position().z
        )

        "<aqua>-<reset> ${enginePlayer.displayName} (${enginePlayer.username})<newline>" +
                "<aqua>Координаты:</aqua><newline>  <red>Engine:</red> ${enginePosFormatted}<newline>  <green>Minecraft:</green> ${minecraftPosFormatted}<newline>" +
                "<aqua>Предметы:</aqua> ${enginePlayer.items.joinToString()}"
    }
}

typealias ServerCommandDispatcher = CommandDispatcher<CommandSourceStack>

typealias ServerCommandContext = CommandContext<CommandSourceStack>

private val LOGGER = LoggerFactory.getLogger("Engine Fabric Commands")

fun ServerCommandContext.getPlayers(id: String): List<ServerPlayer> {
    return EntityArgument.getPlayers(this, id).toList()
}

fun ServerCommandContext.getPlayerEntity(id: String): ServerPlayer {
    return EntityArgument.getPlayer(this, id)
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

fun ServerCommandContext.getVec3(id: String): Vec3 {
    return Vec3Argument.getVec3(this, id)
}

fun <T : ArgumentBuilder<CommandSourceStack, T>> ArgumentBuilder<CommandSourceStack, T>.executeCatching(todo: (Context) -> Unit): T {
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
            source.sendFailure(e.message!!.parseMiniMessage())
        } catch (e: Throwable) {
            source.sendFailure(literalText(e.message ?: "Неизвестная ошибка"))
            LOGGER.error("Возникла ошибка при выполнении команды ${e.message}", e)
        }
        1
    }
}

fun CommandSourceStack.hasPermission(text: String): Boolean {
    if (permissions().hasPermission(Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))) return true
    if (player?.hasPermission(text) == true) return true
    return false
}

class FriendlyException(message: String) : Exception(message)

fun friendlyError(message: String): Nothing = throw FriendlyException(message)

fun List<ServerPlayer>.formatPlayerList() = joinToString(separator = ", ") { it.name.string }

data class Context(
    val player: EnginePlayer?,
    val source: CommandSourceStack,
    val command: ServerCommandContext
) {
    fun requirePlayer(): EnginePlayer {
        return player ?: throw FriendlyException("Команда предназначена для игрока")
    }

    fun requireEntity(): Entity {
        return source.entity ?: throw FriendlyException("Команда предназначена для сущностей или игроков")
    }

    fun sendFeedback(text: String, broadcastToOps: Boolean) {
        source.sendSuccess({ text.parseMiniMessage() }, broadcastToOps)
    }

    fun sendError(text: String) {
        source.sendFailure(text.parseMiniMessage())
    }

    fun sendError(exception: Throwable) {
        source.sendFailure(literalText(exception.message ?: "При выполнении команды возникла ошибка. Свяжитесь с администратором"))
    }
}

fun literal(name: String) = Commands.literal(name)

fun <T : Any> argument(name: String, argumentType: ArgumentType<T>): RequiredArgumentBuilder<CommandSourceStack, T> = Commands.argument<T>(name, argumentType)

fun stringArgument(name: String) = argument(name, StringArgumentType.string())

fun wordArgument(name: String) = argument(name, StringArgumentType.word())

fun floatArgument(name: String) = argument(name, FloatArgumentType.floatArg())

fun floatArgument(name: String, min: Float, max: Float) = argument(name, FloatArgumentType.floatArg(min, max))

fun selection(name: String, variants: List<String>) = argument(name, StringArgumentType.word())
    .suggests(StringListSuggestionProvider(variants))

class StringListSuggestionProvider(val variants: List<String>) : SuggestionProvider<CommandSourceStack> {
    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        variants
            .filter { it.startsWith(builder.remainingLowerCase) }
            .forEach { variant -> builder.suggest(variant) }
        return builder.buildFuture()
    }
}

fun ServerCommandDispatcher.registerEngineCommands(isDedicated: Boolean) {
    val playerTable = injectValue<EntityTable>().server
    val server by injectMinecraftEngineServer()

    registerEngineReloadCommands(isDedicated)
    registerEngineDeveloperCommands()

    register(
        literal("speed")
            .requires { it.hasPermission("attributecommand.speed") }
            .then(
                Commands.argument("players", EntityArgument.players())
                    .then(
                        Commands.literal("reset")
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
                        literal("set")
                            .then(
                                Commands.argument("value", FloatArgumentType.floatArg())
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
        literal("jumpstrength")
            .requires { it.hasPermission("attributecommand.jumpstrength") }
            .then(
                Commands.argument("players", EntityArgument.players())
                    .then(
                        literal("set")
                            .then(
                                argument("value", FloatArgumentType.floatArg())
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
                        literal("reset")
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
        literal("setname")
            .then(
                argument("args", StringArgumentType.greedyString())
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
        literal("spawn")
            .executeCatching { ctx ->
                val player = ctx.requirePlayer()
                player.stopSpectating()
            }
    )

    register(
        literal("sourcemessage")
            .requires { it.hasPermission("sourcemessage") }
            .then(
                argument("pos", Vec3Argument.vec3())
                    .then(
                        Commands.argument("author", StringArgumentType.string()
                        )
                            .then(
                                Commands.argument("volume", FloatArgumentType.floatArg())
                                    .then(
                                        Commands.argument("text", StringArgumentType.greedyString())
                                            .executeCatching { ctx ->
                                                val source = ctx.source
                                                val engine = server.engine
                                                val world = engine.getWorld(source.level.engine)
                                                val text = ctx.command.getString("text")
                                                val author = ctx.command.getString("author")
                                                val volume = ctx.command.getFloat("volume")
                                                val pos = Vec3Argument.getVec3(ctx.command, "pos")
                                                val chat = engine.chat

                                                val channel = chat.settings.defaultChannel
                                                val message = IncomingMessage(
                                                    text,
                                                    volume,
                                                    channel.id,
                                                    MessageSource.getWorld(
                                                        world,
                                                        author,
                                                        channel,
                                                        ImmutableEVec3(pos.engine())
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
        literal("speak")
            .requires { it.hasPermission("speak") }
            .then(
                argument("player", EntityArgument.player(),
                )
                    .then(
                        argument("text", StringArgumentType.greedyString())
                            .executeCatching { ctx ->
                                val text = ctx.command.getString("text")
                                val entity = ctx.command.getPlayerEntity("player")
                                val player = playerTable.requirePlayer(entity)

                                val chat = server.engine.chat
                                val channels = ClientChatSettings.channelsOf(chat.settings, player)
                                val (channel, content) = chatChannelOf(
                                    text,
                                    channels.values.toList(),
                                    ClientChatChannel.of(chat.settings.defaultChannel, player)
                                )

                                player.speak(content, channel.id, player.volume)
                            }
                    )
            )
    )

    register(
        literal("reloadacousticchunks")
            .requires { it.hasPermission("reloadacousticchunks") }
            .executeCatching { ctx ->
                server.acousticSimulator.invalidate()
                ctx.sendFeedback("Акустические чанки сброшены", true)
            }
    )

    register(
        Commands.literal("voicebreak")
            .requires { it.hasPermission("voicebreak") }
            .then(
                argument("player", EntityArgument.player())
                    .then(
                        literal("status")
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
                        literal("remove")
                            .executeCatching { ctx ->
                                val player = ctx.command.getPlayer("player")

                                player.apply<VoiceApparatus> { tiredness = 0f }
                                player.remove<VoiceLoose>()?.let { ctx.sendFeedback("Голос игрока ${player.username} восстановлен", true) }
                            }
                    )
        )
    )

    register(
        literal("engineitem")
            .requires { it.hasPermission("engineitem") }
            .then(
                argument("id", StringArgumentType.string())
                    .suggests(
                        NamespacedIdProvider { it.items.ids }
                    )
                    .executeCatching { ctx ->
                        val argument = ctx.command.getString("id")
                        val id = ItemId(argument)
                        val player = ctx.source.player ?: friendlyError("Команда доступна только игроку")

                        val storage = server.engine.namespacedStorage.get()
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
                                player.addItem(copy)
                                copy
                            }
                        }

                        val format = if (prefabs.count() == 1) {
                            "Выдан предмет %s игроку %s"
                        } else {
                            "Выданы предметы %s игроку %s"
                        }

                        val text = String.format(format, argument, player.displayName?.string)
                        ctx.sendFeedback(text, true)
                    }
            )
    )

    fun executeEngineSoundCommand(ctx: Context, id: String, pos: Vec3? = null, volume: Float = 1f) {
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
        literal("enginesound")
            .requires { it.hasPermission("enginesound") }
            .then(
                argument("id", StringArgumentType.string())
                    .suggests(
                        NamespacedIdProvider { it.sounds.ids }
                    )
                    .executeCatching { ctx ->
                        val argument = ctx.command.getString("id")
                        executeEngineSoundCommand(ctx, argument)
                    }
                    .then(
                        argument("pos", Vec3Argument.vec3())
                            .executeCatching { ctx ->
                                val argument = ctx.command.getString("id")
                                val pos = ctx.command.getVec3("pos")
                                executeEngineSoundCommand(ctx, argument, pos)
                            }
                            .then(
                                argument("volume", FloatArgumentType.floatArg())
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
        Commands.literal("chatheads")
            .requires { it.hasPermission(CHAT_HEADS_PERMISSION) }
            .executeCatching {
                val player = it.requirePlayer()
                val enabled = player.toggleChatHeads()
                it.sendFeedback("Отображение иконки персонажа ${if (enabled) "включено" else "отключено"}", false)
            }
    )

    fun getLookedBlock(entity: Entity): Pair<BlockPos, LevelChunk> {
        val lookPos = entity.pick(10.0, 0.0f, false).location
        val blockPos = BlockPos.containing(lookPos)
        val chunkPos = entity.chunkPosition()
        val chunk = entity.level().getChunk(chunkPos.x, chunkPos.z)
        return blockPos to chunk
    }

    register(
        Commands.literal("interactions")
            .executeCatching { ctx ->
                val player = ctx.requirePlayer()
                val actions = mutableSetOf(InputAction.Base, InputAction.Attack)
                val cursorItem = player.cursorItem
                val handItem = player.handItem
                if (cursorItem != null && handItem != null) {
                    actions += InputAction.SlotClick(cursorItem, handItem)
                }
                val input = PlayerInput(actions, setOf())
                updatePlayerVerbLookup(player, true, input)
                with(player.world) { appendVerbs(player) }
                val lookup = player.remove<VerbLookup>()!!
                val text = Text.empty()
                text.append(
                    Text.literal("Список доступных взаимодействий:")
                        .withStyle(ChatFormatting.GREEN)
                )
                for (variant in lookup.verbs.sortedBy { it.verb.priority }) {
                    val action = when(variant.action) {
                        InputAction.Attack -> "Атаковать"
                        InputAction.Base -> "Взаимодействовать"
                        InputAction.TakeOff -> "Снять экипировку"
                        is InputAction.SlotClick -> "Нажать на слот"
                    }
                    val verb = variant.verb
                    val name = verb.name
                    text.append("\n")
                    text.append("- ").withStyle(ChatFormatting.GRAY)
                    text.append("$action: $name").withStyle(ChatFormatting.WHITE)
                }
                ctx.command.source.sendSuccess({ text }, false)
            }
    )

    register(
        literal("eye")
            .then(
                floatArgument("y", -16f, 16f)
                    .executeCatching { ctx ->
                        val player = ctx.requirePlayer()
                        val value = ctx.command.getFloat("y") * 0.01f
                        player.skinEyeY = value
                        ctx.sendFeedback("Установлена высота глаз на $value пикселей", false)
                    }
            )
    )

    fun registerPlayerScriptCommand(
        name: String,
        successMessage: (scriptId: ScriptId) -> String,
        setter: ScriptBindings.(ScriptId?) -> Unit
    ) {
        register(
            literal(name)
                .then(
                    stringArgument("script")
                        .suggests(NamespacedIdProvider(listOf("none")) { it.scripts.ids })
                        .executeCatching { ctx ->
                            val player = ctx.requirePlayer()

                            val scriptArg = ctx.command.getString("script")
                            if (scriptArg != "none") {
                                val scriptId = scriptArg
                                    .replace("\"", "")
                                    .toScriptId()

                                server.engine.namespacedStorage
                                    .getVoidScript<ScriptContext.Player>(scriptId)
                                    ?: friendlyError("Скрипт $scriptId не найден")

                                player.require<ScriptBindings>().setter(scriptId)
                                ctx.sendFeedback(successMessage(scriptId), true)
                            } else {
                                player.require<ScriptBindings>().setter(null)
                            }
                        }
                )
        )
    }

    registerPlayerScriptCommand(
        name = "attackscript",
        successMessage = { "Установлен скрипт атаки игрока на $it" }
    ) {
        attack = it
    }

    registerPlayerScriptCommand(
        name = "basescript",
        successMessage = { "Установлен скрипт взаимодействия игрока на $it" }
    ) {
        base = it
    }

    registerServerPmCommand()
}

class NamespacedIdProvider(
    val additional: List<String> = listOf(),
    val provider: (NamespacedStorageAccess) -> List<String>,
) : SuggestionProvider<CommandSourceStack> {
    private val server by injectEngineServer()
    private val storage get() = server.namespacedStorage

    override fun getSuggestions(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val input = builder.remainingLowerCase.replace(""""""", "")
        val identifiers = provider(storage)
        (identifiers
            .filter {
                it.startsWith(input) || it.split("/").any { it.startsWith(input) }
            } + additional)
            .forEach { builder.suggest('"' + it + '"') }
        return builder.buildFuture()
    }
}