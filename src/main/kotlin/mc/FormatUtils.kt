package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.RaycastProvider
import org.lain.engine.player.Username
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.asVec3
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelPos

fun MinecraftUsername(player: PlayerEntity) = Username(player.name.string)

val PlayerEntity.engineId
    get() = PlayerId(this.uuid)

fun Pos.toBlockPos(): BlockPos = BlockPos.ofFloored(asVec3().toMinecraft())

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
    override fun isPlayerSeeOther(player: EnginePlayer, seen: EnginePlayer): Boolean {
        val entity1 = entityTable.getEntity(player) ?: return false
        val entity2 = entityTable.getEntity(player) ?: return false
        return entity1.canSee(entity2)
    }
}

fun MutableVec3.set(vec3: Vec3d) {
    this.x = vec3.x.toFloat()
    this.y = vec3.y.toFloat()
    this.z = vec3.z.toFloat()
}

fun MinecraftServer.getPlayer(id: PlayerId) = playerManager.getPlayer(id.value)

fun ChunkPos.engine() = EngineChunkPos(x, z)

fun BlockPos.engine() = VoxelPos(this.x, this.y, this.z)