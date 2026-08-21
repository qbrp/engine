package org.lain.engine.client.mc

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import org.lain.cyberia.ecs.hasComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.GameSession
import org.lain.engine.mc.MinecraftAccess
import org.lain.engine.mc.engineId
import org.lain.engine.mc.executePlaceVoxelCallback
import org.lain.engine.mc.getEngineState
import org.lain.engine.mc.immutableVoxelPos
import org.lain.engine.mc.voxelPos
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.world.ImmutableVoxelPos

class ClientMinecraftAccess(
    private val client: EngineMinecraftClient,
    private val gameSession: GameSession
) : MinecraftAccess {
    private val world
        get() = gameSession.world
    override val simulation: EngineSimulation
        get() = gameSession.simulation

    override fun onBlockPlaced(
        player: Player?,
        blockPos: BlockPos,
        blockState: BlockState,
        level: Level
    ) {
        gameSession.simulation.callbacks.executePlaceVoxelCallback(
            player?.let { getEnginePlayer(it) },
            world,
            blockPos.voxelPos(),
            blockState
        )
    }

    override fun onBlockDestroyed(level: Level, blockPos: BlockPos) {
        val pos = ImmutableVoxelPos(blockPos.immutableVoxelPos())
        world.chunkStorage.removeVoxel(pos)
        client.decalSystem.unloadTexture(pos)
    }

    override fun onBlockInteraction(player: Player, level: Level, blockPos: BlockPos): Boolean =
        with(world) {
            val voxel = chunkStorage.getDynamicVoxel(blockPos.immutableVoxelPos()) ?: return false
            voxel.hasComponent(CoreScriptComponents.USE_RESTRICTION)
        }
}