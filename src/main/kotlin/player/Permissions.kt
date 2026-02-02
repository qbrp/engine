package org.lain.engine.player

import org.lain.engine.util.Injector

interface PlayerPermissionsProvider {
    fun hasPermission(player: EnginePlayer, permission: String): Boolean
}

fun EnginePlayer.hasPermission(permission: String): Boolean {
    return Injector.resolve<PlayerPermissionsProvider>().hasPermission(this, permission)
}

