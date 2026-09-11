package org.lain.engine.chat

import org.lain.engine.player.EnginePlayer

const val CHAT_OPERATOR_PERMISSION = "chat.operator"

const val CHAT_HEADS_PERMISSION = "chat.heads"

fun EnginePlayer.hasPermission(permission: String): Boolean {
    if (world.isClient) return true
    return world.server?.hasPermission(this, permission) == true
}

val EnginePlayer.isChatOperator
    get() = hasPermission(CHAT_OPERATOR_PERMISSION)

fun EnginePlayer.isChannelAvailableToRead(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.read")

fun EnginePlayer.isChannelAvailableToWrite(channel: ChatChannel) = !channel.permission || hasPermission("chat.${channel.id.value}.write")

fun EnginePlayer.isChannelAvailable(channel: ChatChannel) = isChannelAvailableToWrite(channel) || isChannelAvailableToRead(channel)