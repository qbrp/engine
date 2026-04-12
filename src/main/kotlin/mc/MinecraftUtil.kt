package org.lain.engine.mc

import net.minecraft.block.BlockState
import net.minecraft.block.ShapeContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.projectile.ProjectileUtil
import net.minecraft.predicate.entity.EntityPredicates
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.tag.TagKey
import net.minecraft.server.MinecraftServer
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.player.RaycastProvider
import org.lain.engine.player.Username
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptContext
import org.lain.engine.util.math.MutableVec3
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.asVec3
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.World

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

class MinecraftRaycastProvider(private val playerTable: EntityTable) : RaycastProvider {
    override fun whoSee(player: EnginePlayer, distance: Int, isClient: Boolean): EnginePlayer? {
        val table = if (isClient) playerTable.client else playerTable.server
        val entity1 = table.getEntity(player) ?: return null
        val results = ProjectileUtil.raycast(
            entity1,
            entity1.eyePos,
            entity1.eyePos.add(entity1.rotationVector.multiply(distance.toDouble())),
            entity1.boundingBox
                .stretch(entity1.rotationVector.multiply(distance.toDouble()))
                .expand(1.0),
            EntityPredicates.CAN_HIT,
            distance.toDouble(),
        );
        return (results?.entity as? PlayerEntity?)?.let {
            (table as EntityTable.Entity2PlayerTable<PlayerEntity>).getPlayer(it)
        }
    }

    override fun canSee(player: EnginePlayer, voxelPos: VoxelPos, isClient: Boolean): Boolean {
        val table = if (isClient) playerTable.client else playerTable.server
        val entity = table.getEntity(player) ?: return false
        val blockPos =BlockPos(voxelPos.x, voxelPos.y, voxelPos.z)
        val context = RaycastContext(
            entity.eyePos,
            blockPos.toCenterPos(),
            RaycastContext.ShapeType.VISUAL,
            RaycastContext.FluidHandling.WATER,
            ShapeContext.absent()
        )
        val raycastResult = entity.entityWorld.raycast(context)
        return raycastResult != null && (raycastResult.type == HitResult.Type.MISS || raycastResult.blockPos == blockPos)
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

fun Callbacks.executePlaceVoxelCallback(player: EnginePlayer?, world: World, pos: VoxelPos, state: BlockState) {
    placeVoxel.execute(
        ScriptContext.VoxelAction(
            player,
            world,
            pos,
            object : VoxelMeta {
                override val id: String
                    get() = state.block.registryEntry.idAsString

                override fun hasTag(id: String): Boolean {
                    return state.isIn(TagKey.of(RegistryKeys.BLOCK, Identifier.ofVanilla(id)))
                }
            }
        )
    )
}