package org.lain.engine.mc.server

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.lain.cyberia.ecs.hasComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.mc.CommonMixin
import org.lain.engine.mc.MinecraftAccess
import org.lain.engine.mc.ServerMixin
import org.lain.engine.mc.getEngineState
import org.lain.engine.mc.voxelPos
import org.lain.engine.script.CoreScriptComponents

class ServerMinecraftAccess(private val server: EngineMinecraftServer) : MinecraftAccess {
    override val simulation: EngineSimulation
        get() = server.engine.simulation

    override fun onBlockPlaced(
        player: Player?,
        blockPos: BlockPos,
        blockState: BlockState,
        level: Level
    ) {
        server.onBlockAdd(player?.let { getEnginePlayer(it) }, blockPos, blockState, level)
    }

    override fun onBlockDestroyed(level: Level, blockPos: BlockPos) {
        server.onBlockBreak(blockPos, level)
    }

    override fun onBlockInteraction(player: Player, level: Level, blockPos: BlockPos): Boolean {
        val world = requireEngineWorld(level)
        val voxel = world.chunkStorage.getDynamicVoxel(blockPos.voxelPos()) ?: return false
        return with(world) {
            voxel.hasComponent(CoreScriptComponents.USE_RESTRICTION)
        }
    }
}