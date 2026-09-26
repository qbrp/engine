package org.lain.engine.mc

import com.sun.jna.Native
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.player.*
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.server.Notification
import org.lain.engine.util.requireEngineMinecraftServer
import org.lain.engine.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface MinecraftAccess {
    val simulation: EngineSimulation

    fun requireEnginePlayer(entity: Player): EnginePlayer {
        return simulation.players.require(entity)
    }

    fun getEnginePlayer(entity: Player): EnginePlayer? {
        return simulation.players.get(entity)
    }

    fun requireEngineWorld(level: Level): World =
         simulation.worlds[level.engineId] ?: error("Engine world for $level not exists")

    fun onBlockDestroyed(level: Level, blockPos: BlockPos) {}
    fun onBlockPlaced(player: Player?, blockPos: BlockPos, blockState: BlockState, level: Level) {}
    fun onBlockInteraction(player: Player, level: Level, blockPos: BlockPos): Boolean = false
}

object MinecraftAccessRegistry {
    private val byLevel = ConcurrentHashMap<Level, MinecraftAccess>()

    fun invalidate() {
        byLevel.clear()
    }

    fun register(level: Level, access: MinecraftAccess) {
        byLevel[level] = access
    }

    fun getOrNull(level: Level): MinecraftAccess? {
        return byLevel[level]
    }

    fun get(level: Level): MinecraftAccess {
        return getOrNull(level) ?: error("Mixin access not registered for level $level")
    }

    fun getEnginePlayer(player: Player) = getOrNull(player.level())?.getEnginePlayer(player)

    fun requireEnginePlayer(player: Player) = get(player.level()).requireEnginePlayer(player)
}

fun Level.engineAccess() = MinecraftAccessRegistry.getOrNull(this)

fun Level.requireEngineAccess() = MinecraftAccessRegistry.get(this)

fun Level.engineState() = engineAccess()?.requireEngineWorld(this)