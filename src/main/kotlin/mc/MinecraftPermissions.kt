package org.lain.engine.mc

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

val Player.isOp
    get() = this is ServerPlayer && server.playerList.isOp(gameProfile)

fun Player.hasPermission(perm: String): Boolean {
    return this.isOp || Permissions.check(this, "engine.$perm")
}
