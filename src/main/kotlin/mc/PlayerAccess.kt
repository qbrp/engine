package org.lain.engine.mc

import net.minecraft.world.entity.player.Player
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.get

fun PlayerStorage.require(entity: Player) = require(entity.engineId)

fun PlayerStorage.get(entity: Player) = get(entity.engineId)

fun Player.requireEngineState(): EnginePlayer {
    return MinecraftAccessRegistry.requireEnginePlayer(this)
}

fun Player.getEngineState(): EnginePlayer? {
    return MinecraftAccessRegistry.getEnginePlayer(this)
}

fun replacePlayerMinecraftState(entity: Player) {
    val enginePlayer = entity.getEngineState() ?: return
    val oldEntity = enginePlayer.get<MinecraftPlayer>()
    oldEntity?.entity = entity
    //TODO: проверить, можно ли переместить в тик игрока
}