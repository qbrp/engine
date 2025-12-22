package org.lain.engine.player

import org.lain.engine.chat.ChatChannel
import org.lain.engine.util.Injector

interface PlayerPermissionsProvider {
    fun hasPermission(player: Player, permission: String): Boolean
}

fun Player.hasPermission(permission: String): Boolean {
    return Injector.resolve<PlayerPermissionsProvider>().hasPermission(this, permission)
}

