package org.lain.engine.mc

import net.minecraft.world.entity.player.Player
import org.lain.engine.mc.ecs.MinecraftPlayer
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.get
import org.lain.engine.player.set

fun PlayerStorage.require(entity: Player) = require(entity.engineId)

fun PlayerStorage.get(entity: Player) = get(entity.engineId)

fun Player.requireEngineState(): EnginePlayer {
    return MinecraftAccessRegistry.requireEnginePlayer(this)
}

fun Player.getEngineState(): EnginePlayer? {
    return MinecraftAccessRegistry.getEnginePlayer(this)
}

internal fun EnginePlayer.bindMinecraftEntity(entity: Player): Boolean {
    val minecraftPlayer = get<MinecraftPlayer>()
    if (minecraftPlayer == null) {
        set(MinecraftPlayer(entity))
        return true
    }
    if (minecraftPlayer.entity === entity) return false

    minecraftPlayer.entity = entity
    return true
}

fun replacePlayerMinecraftState(entity: Player) {
    val enginePlayer = entity.getEngineState() ?: return
    enginePlayer.bindMinecraftEntity(entity)
}
