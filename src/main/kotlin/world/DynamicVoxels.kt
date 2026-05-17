package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.Networked
import org.lain.engine.util.math.EVec3

data class DynamicVoxel(val pos: ImmutableVoxelPos) : Component

data class ChunkedPos(
    val pos: EngineChunkPos,
    val voxelPos: ImmutableVoxelPos,
    val centerPos: EVec3
) : Component

fun World.setDynamicVoxel(pos: VoxelPos, networked: Boolean = false): EntityId {
    val entity = addEntity()
    entity.setDynamicVoxel(pos, networked)
    chunkStorage.requireChunk(pos).dynamicVoxels[pos] = entity
    return entity
}

context(access: World)
fun EntityId.setDynamicVoxel(pos: VoxelPos, networked: Boolean = false) {
    val centerPos = pos.toCenterPos()
    val immutableVoxelPos = ImmutableVoxelPos(pos)
    setComponent(DynamicVoxel(immutableVoxelPos))
    setComponent(ChunkedPos(EngineChunkPos(pos), immutableVoxelPos, centerPos))
    if (networked) setComponent(Networked)
    setComponent(Location(access, pos.toCenterPos()))
}