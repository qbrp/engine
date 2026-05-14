package org.lain.engine.mc

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionLevel
import net.minecraft.world.entity.player.Player
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPermissionsProvider

class MinecraftPermissionProvider(private val entityTable: ServerPlayerTable) : PlayerPermissionsProvider {
    override fun hasPermission(player: EnginePlayer, permission: String): Boolean {
        val entity = entityTable.getEntity(player) as? ServerPlayer ?: return false
        return entity.hasPermission(permission)
    }
}

val Player.isOp
    get() = this.permissions().hasPermission(Permission.HasCommandLevel(PermissionLevel.OWNERS))

fun Player.hasPermission(perm: String): Boolean {
    return this.isOp || Permissions.check(this, "engine.$perm")
}