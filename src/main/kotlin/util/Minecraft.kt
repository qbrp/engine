package org.lain.engine.util

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import org.lain.engine.mc.EntityTable
import org.lain.engine.mc.ServerPlayerTable
import org.lain.engine.mc.Username
import org.lain.engine.mc.engine
import org.lain.engine.player.*
import org.lain.engine.world.World

fun MinecraftUsername(player: PlayerEntity) = Username(player.name.string)

val PlayerEntity.engineId
    get() = PlayerId(this.uuid)

fun Pos.toBlockPos(): BlockPos = BlockPos(x.toInt(), y.toInt(), z.toInt())

fun chunkSquare(from: ChunkPos, size: Int): Array<ChunkPos> {
    return Array<ChunkPos>(size * size) { i ->
        val z = i / size
        val x = i % size
        ChunkPos(from.x + x, from.z + z)
    }
}

fun minecraftChunkSectionCoord(value: Int): Int {
    return value shr 4
}

class MinecraftRaycastProvider(
    private val server: MinecraftServer,
    private val entityTable: ServerPlayerTable
) : RaycastProvider {
    override fun isPlayerSeeOther(player: Player, seen: Player): Boolean {
        val entity1 = entityTable.getEntity(player) ?: return false
        val entity2 = entityTable.getEntity(player) ?: return false
        return entity1.canSee(entity2)
    }
}