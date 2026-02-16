package org.lain.engine.client.mc

import net.minecraft.client.world.ClientWorld
import org.lain.engine.client.mc.render.ChunkDecalsStorage
import org.lain.engine.item.BulletParameters
import org.lain.engine.item.DEFAULT_BULLET_MASS
import org.lain.engine.item.DEFAULT_BULLET_SPEED
import org.lain.engine.item.GunShoot
import org.lain.engine.mc.addBulletDecal
import org.lain.engine.mc.engine
import org.lain.engine.mc.raycastBulletEvent
import org.lain.engine.util.flush
import org.lain.engine.util.math.Pos
import org.lain.engine.world.World
import org.lain.engine.world.events

fun updateBulletsVisual(
    world: World,
    mcWorld: ClientWorld,
    decalManager: ChunkDecalsStorage
) = world.events<GunShoot>().flush { event ->
    val hitResult = raycastBulletEvent(mcWorld, event) ?: return@flush
    val blockPos = hitResult.blockPos
    val chunk = mcWorld.getChunk(blockPos)
    repeat(5) { mcWorld.spawnBlockBreakingParticle(blockPos, hitResult.side) }

    val pos = hitResult.pos.engine()
    world.events<BulletHit>().add(BulletHit(pos, BulletParameters(DEFAULT_BULLET_MASS, DEFAULT_BULLET_SPEED)))

    addBulletDecal(chunk, blockPos, pos, hitResult.side.engine())
    decalManager.update(chunk, blockPos)
}

data class BulletHit(val pos: Pos, val bullet: BulletParameters)