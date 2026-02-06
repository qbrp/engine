package org.lain.engine.client.mc

import net.minecraft.block.ShapeContext
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Direction
import net.minecraft.world.RaycastContext
import org.lain.engine.client.mc.render.ChunkDecalsStorage
import org.lain.engine.item.SoundPlay
import org.lain.engine.item.WorldGunEvents
import org.lain.engine.mc.addBulletDecal
import org.lain.engine.mc.engine
import org.lain.engine.mc.raycastBulletEvent
import org.lain.engine.mc.toMinecraft
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.ShakeScreenComponent
import org.lain.engine.player.shake
import org.lain.engine.util.flush
import org.lain.engine.util.require
import org.lain.engine.util.set
import org.lain.engine.world.World
import org.lain.engine.world.pos

fun updateBulletsVisual(
    world: World,
    mcWorld: ClientWorld,
    mainPlayer: EnginePlayer,
    decalManager: ChunkDecalsStorage
) = world.require<WorldGunEvents>().bullet.flush { event ->
    val hitResult = raycastBulletEvent(mcWorld, event) ?: return@flush
    val blockPos = hitResult.blockPos
    val chunk = mcWorld.getChunk(blockPos)
    repeat(5) { mcWorld.spawnBlockBreakingParticle(blockPos, hitResult.side) }

    val distance = mainPlayer.pos.squaredDistanceTo(blockPos.toCenterPos().engine())
    val d = 8 * 8
    if (distance < d) {
        mainPlayer.shake(((d - distance) / d) * 0.2f)
    }

    addBulletDecal(chunk, blockPos, hitResult.pos.engine(), hitResult.side.engine())
    decalManager.update(chunk, blockPos)
}