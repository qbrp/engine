package org.lain.engine.chat

import org.lain.engine.player.Player
import org.lain.engine.player.hasPermission

const val CHAT_OPERATOR_PERMISSION = "chat.operator"

const val CHAT_HEADS_PERMISSION = "chat.heads"

val Player.isChatOperator
    get() = this.hasPermission(CHAT_OPERATOR_PERMISSION)

fun Player.isChannelAvailableToRead(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.read")

fun Player.isChannelAvailableToWrite(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.write")

fun Player.isChannelAvailable(channel: ChatChannel) = isChannelAvailableToWrite(channel) || isChannelAvailableToRead(channel)