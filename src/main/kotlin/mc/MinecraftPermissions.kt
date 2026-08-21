package org.lain.engine.mc

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.entity.player.Player

val Player.isOp
    get() = this.permissions().hasPermission(Permission.HasCommandLevel(PermissionLevel.OWNERS))

fun Player.hasPermission(perm: String): Boolean {
    return this.isOp || Permissions.check(this, "engine.$perm")
}