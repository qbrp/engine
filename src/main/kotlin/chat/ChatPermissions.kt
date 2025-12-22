package org.lain.engine.chat

import org.lain.engine.player.Player
import org.lain.engine.player.hasPermission
import org.lain.engine.util.Component
import org.lain.engine.util.has
import org.lain.engine.util.require
import org.lain.engine.util.set

const val CHAT_OPERATOR_PERMISSION = "chat.operator"

val Player.isChatOperator
    get() = this.hasPermission(CHAT_OPERATOR_PERMISSION)

fun Player.isChannelAvailableToRead(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.read")

fun Player.isChannelAvailableToWrite(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.write")

fun Player.isChannelAvailable(channel: ChatChannel) = isChannelAvailableToWrite(channel) || isChannelAvailableToRead(channel)