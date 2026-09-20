package org.lain.engine.world

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.ScriptEngine
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.PersistentId
import org.lain.engine.data.VoxelPosId
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.server.Networked
import org.lain.engine.util.math.EVec3

object DynamicVoxelInterest : Component

data class DynamicVoxel(val pos: ImmutableVoxelPos) : Component

data class ChunkedPos(
    val pos: EngineChunkPos,
    val voxelPos: ImmutableVoxelPos,
    val centerPos: EVec3
) : Component

context(scriptEngine: ScriptEngine)
fun World.setDynamicVoxel(pos: VoxelPos, networked: Boolean = false): EntityId {
    val chunk = chunkStorage.requireChunk(pos)
    val entity = addEntity()
    val voxelPos = ImmutableVoxelPos(pos)
    val persistentId = VoxelPosId(id, voxelPos)
    entity.setDynamicVoxel(pos, persistentId, networked)
    server?.entityCoordinator?.registerEntity(this, persistentId, entity)
    chunk.dynamicVoxels[voxelPos] = entity
    return entity
}

context(scriptEngine: ScriptEngine, access: WriteComponentAccess)
fun EntityId.setDynamicVoxel(
    pos: VoxelPos,
    persistentId: PersistentId,
    networked: Boolean = false,
) {
    val centerPos = pos.toCenterPos()
    val immutableVoxelPos = ImmutableVoxelPos(pos)
    setComponent(DynamicVoxel(immutableVoxelPos))
    setComponent(DynamicVoxelInterest)
    setComponent(PersistentIdComponent(persistentId))
    setComponent(ChunkedPos(EngineChunkPos(pos), immutableVoxelPos, centerPos))
    if (networked) setComponent(Networked)
    setComponent(Location(pos.toCenterPos()))
    setComponent(
        scriptEngine.createScriptComponent(
            SList(
                listOf(
                    SInt(pos.x),
                    SInt(pos.y),
                    SInt(pos.z)
                )
            ),
            CoreScriptComponents.DYNAMIC_VOXEL
        )
    )
}
