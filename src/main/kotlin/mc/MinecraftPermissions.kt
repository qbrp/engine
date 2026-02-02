package org.lain.engine.mc

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPermissionsProvider

class MinecraftPermissionProvider(private val entityTable: ServerPlayerTable) : PlayerPermissionsProvider {
    override fun hasPermission(player: EnginePlayer, permission: String): Boolean {
        val entity = entityTable.getEntity(player) as? ServerPlayerEntity ?: return false
        return entity.hasPermission(permission)
    }
}

val PlayerEntity.isOp
    get() = this.hasPermissionLevel(4)

fun PlayerEntity.hasPermission(perm: String): Boolean {
    return this.isOp || Permissions.check(this, "engine.$perm")
}