package org.lain.engine.mc.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.messageSource
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.mc.hasPermission
import org.lain.engine.player.developerMode
import org.lain.engine.player.username
import org.lain.engine.util.injectEngineServer
import org.lain.engine.util.injectEntityTable

/**
 * Регистрация команды типа `/<название> <содержание>`, отправляющий содержание в чат-каналs
 * @param name Название команды (/команда)
 * @param channel В какой канал будет отправляться `содержание`
 * @param argument Название аргумента команды
 * @param permission Требуется ли разрешение
 */
fun ServerCommandDispatcher.registerServerChatCommand(
    name: String,
    channel: ChatChannel,
    argument: String = "text",
    permission: Boolean = false,
    aliases: List<String> = listOf(),
) {
    val engine by injectEngineServer()
    val node = Commands.literal(name)
        .requires { !permission || it.player?.hasPermission("chat.$name") == true }
        .then(
            Commands.argument(argument, StringArgumentType.greedyString())
                .executeCatching { ctx ->
                    val text = ctx.command.getString(argument)
                    val player = ctx.requirePlayer()
                    engine.chat.processMessage(channel, MessageSource.getPlayer(player, channel), text)
                }
        )
        .build()
    root.addChild(node)
    aliases.forEach { alias -> register(Commands.literal(alias).redirect(node)) }
}

fun ServerCommandDispatcher.registerServerPmCommand() {
    val engine by injectEngineServer()
    val table by injectEntityTable()

    register(
        Commands.literal("pm")
            .then(
                Commands.argument("player", EntityArgument.player())
                    .then(
                        Commands.argument("text", StringArgumentType.greedyString())
                            .executeCatching { ctx ->
                                val text = ctx.command.getString("text")
                                val recipient = ctx.command.getPlayerEntity("player")
                                val authorPlayer = ctx.requirePlayer()
                                val recipientPlayer = table.server.requirePlayer(recipient)

                                if (recipientPlayer == authorPlayer && !authorPlayer.developerMode) {
                                    ctx.sendError("Вы не можете написать самому себе")
                                    return@executeCatching
                                }

                                val channel = engine.chat.settings.pmChannel
                                engine.chat.sendMessage(
                                    text,
                                    MessageSource.getPlayer(authorPlayer, channel),
                                    channel,
                                    recipientPlayer.messageSource(channel),
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