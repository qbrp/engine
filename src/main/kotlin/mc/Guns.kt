package org.lain.engine.mc

import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.world.RaycastContext
import net.minecraft.world.chunk.Chunk
import org.lain.engine.item.BulletFireEvent
import org.lain.engine.item.WorldGunEvents
import org.lain.engine.util.flush
import org.lain.engine.util.math.Pos
import org.lain.engine.util.require
import org.lain.engine.world.*

fun spawnGunSmokeParticle(player: PlayerEntity) {
    val lookVector = player.rotationVector.normalize()
    val pos = player.eyePos.add(lookVector.multiply(0.5))
    repeat(6) { i ->
        player.entityWorld.addParticleClient(
            ParticleTypes.SMOKE,
            false,
            false,
            pos.getX() + (Math.random() * 0.2) - 0.1,
            pos.getY() + (Math.random() * 0.2) - 0.1,
            pos.getZ() + (Math.random() * 0.2) - 0.1,
            (Math.random() * 0.05) - 0.025,
            0.09 + Math.random() * 0.08,
            (Math.random() * 0.05) - 0.025
        )
    }
}

fun raycastBulletEvent(world: net.minecraft.world.World, event: BulletFireEvent): BlockHitResult? {
    val start = event.start
    val end = start.add(event.vector.mul(64))
    val results = world.raycast(
        RaycastContext(
            start.toMinecraft(),
            end.toMinecraft(),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            ShapeContext.absent()
        )
    )
    return if (world.getBlockState(results.blockPos).isAir) {
        null
    } else {
        results
    }
}

fun net.minecraft.util.math.Direction.engine() = when(this) {
    net.minecraft.util.math.Direction.DOWN -> Direction.DOWN
    net.minecraft.util.math.Direction.UP -> Direction.UP
    net.minecraft.util.math.Direction.NORTH -> Direction.NORTH
    net.minecraft.util.math.Direction.SOUTH -> Direction.SOUTH
    net.minecraft.util.math.Direction.WEST -> Direction.WEST
    net.minecraft.util.math.Direction.EAST -> Direction.EAST
}

fun addBulletDecal(chunk: Chunk, blockPos: BlockPos, pos: Pos, direction: Direction) {
    val localX = pos.x - blockPos.x
    val localY = pos.y - blockPos.y
    val localZ = pos.z - blockPos.z

    val (u0, v0) = when (direction) {
        Direction.UP, Direction.DOWN ->
            localX to localZ

        Direction.NORTH, Direction.SOUTH ->
            localX to localY

        Direction.EAST, Direction.WEST ->
            localZ to localY
    }
    val (u, v) = when (direction) {
        Direction.NORTH -> (1.0f - u0) to v0
        Direction.WEST  -> (1.0f - u0) to v0
        Direction.DOWN  -> u0 to (1.0f - v0)
        else -> u0 to v0
    }

    val decal = Decal((u * 16).toInt(), (v * 16).toInt(), DecalContents.Chip(1))

    chunk.updateBlockDecals(blockPos) { decals ->
        decals?.let {
            val layers = it.layers.toMutableList()
            val layer = it.layers[0]
            val map = layer.directions.toMutableMap()
            val list = map.computeIfAbsent(direction) { listOf() }.toMutableList()
            list.add(decal)
            map[direction] = list
            layers[0] = layer.copy(directions = map)
            it.copy(version = it.version + 1, layers = layers)
        } ?: BlockDecals(
            0,
            listOf(
                DecalsLayer(
                    mapOf(
                        direction to listOf(decal)
                    )
                )
            )
        )
    }
}

fun updateBullets(
    world: World,
    mcWorld: ServerWorld,
) = world.require<WorldGunEvents>().bullet.flush { event ->
    val hitResult = raycastBulletEvent(mcWorld, event) ?: return@flush
    val blockPos = hitResult.blockPos
    val pos = hitResult.pos
    val chunk = mcWorld.getChunk(blockPos)
    val dir = hitResult.side.engine()
    addBulletDecal(chunk, blockPos, pos.engine(), dir)
}