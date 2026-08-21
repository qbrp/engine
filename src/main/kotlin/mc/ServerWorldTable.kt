package org.lain.engine.mc

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentHashMap

// Только для сервера
class ServerWorldTable {
    private val worldMap: ConcurrentHashMap<WorldId, Level> = ConcurrentHashMap()

    fun setWorld(id: WorldId, world: Level) {
        worldMap[id] = world
    }

    fun getMcWorld(id: WorldId): Level? {
        return worldMap[id]
    }
}