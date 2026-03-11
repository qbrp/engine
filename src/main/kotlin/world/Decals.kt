package org.lain.engine.world

import kotlinx.serialization.Serializable
import org.lain.engine.util.math.Pos
import org.lain.engine.util.math.Vec3
import org.lain.engine.util.math.floorToInt
import org.lain.engine.util.math.randomFloat

/**
 * # Декали
 * Назначаются на разные стороны блоков, представляют из себя данные о накладываемых на блок картинках.
 * Ввиду воксельного мира и низкого разрешения игры данные компилируются в текстуры в реальном времени.
 * * **На сервере** система декалей отвечает за хранение информации о декалях и перессылку пакетов данных об изменениях тех,
 * что вне зоны досягаемости обычной симуляции
 * * **На клиенте** система отвечает за симуляцию создания декалей, их компиляцию в текстуры и рендеринг
 */

@Serializable
data class Decal(val x: Int, val y: Int, val depth: Float, val contents: DecalContents)

@Serializable
sealed class DecalContents {
    @Serializable
    data class Chip(val radius: Int, val opacity: Float) : DecalContents()
}

typealias Decals = List<Decal>

@Serializable
data class DecalsLayer(val directions: Map<Direction, Decals> = mapOf()) {
    fun withDecal(direction: Direction, decal: Decal): DecalsLayer {
        val map = directions.toMutableMap()
        val list = map.computeIfAbsent(direction) { listOf() }.toMutableList()
        list.add(decal)
        map[direction] = list
        return DecalsLayer(map)
    }

    fun isEmpty() = directions.isEmpty() || directions.all { it.value.isEmpty() }
}

@Serializable
data class DecalsLayerType(val name: String, val resolution: Int)

enum class Direction(val index: Int, val normal: Vec3) {
    DOWN(1, Vec3(0f, -1f, 0f)),
    UP(2, Vec3(0f, 1f, 0f)),
    NORTH(3, Vec3(0f, 0f, -1f)),
    SOUTH(4, Vec3(0f, 0f, 1f)),
    WEST(5, Vec3(-1f, 0f, 0f)),
    EAST(6, Vec3(1f, 0f, 0f));

    companion object {
        fun fromIndex(i: Int) = when(i) {
            1 -> DOWN
            2 -> UP
            3 -> NORTH
            4 -> SOUTH
            5 -> WEST
            6 -> EAST
            else -> error("Invalid index $i")
        }
    }
}

@Serializable
data class BlockDecals(val version: Int = 0, val layers: Map<DecalsLayerType, DecalsLayer> = mapOf()) {
    fun withDecalAtLayer(layer: DecalsLayerType, direction: Direction, decal: Decal): BlockDecals {
        val layers = layers.toMutableMap()
        val newLayer = (layers[layer] ?: DecalsLayer()).withDecal(direction, decal)
        layers[layer] = newLayer
        return BlockDecals(version + 1, layers)
    }

    fun withoutLayers(toRemove: List<DecalsLayerType>): BlockDecals {
        val layers = this.layers.toMutableMap()
        toRemove.forEach { layers.remove(it) }
        return BlockDecals(version + 1, layers)
    }

    fun isEmpty() = layers.isEmpty() || layers.all { it.value.isEmpty() }

    companion object {
        fun withLayer(layer: DecalsLayerType) = BlockDecals(
            0, mapOf(layer to DecalsLayer())
        )
    }
}

val BULLET_DAMAGE_DECALS_LAYER = DecalsLayerType("bullet-damage", 16)
const val MINIMUM_BULLET_DECAL_OPACITY = 0.7f

fun World.attachBulletDamageDecal(direction: Direction, pos: Pos, voxelPos: VoxelPos) {
    attachDecal(
        BULLET_DAMAGE_DECALS_LAYER,
        DecalContents.Chip(
            1,
            MINIMUM_BULLET_DECAL_OPACITY + (1f - MINIMUM_BULLET_DECAL_OPACITY) * randomFloat()
        ),
        direction,
        pos,
        voxelPos,
    )
}


fun World.removeDecals(
    layers: List<DecalsLayerType>,
    positions: List<ImmutableVoxelPos>
) {
    val chunks = positions.groupBy { position -> EngineChunkPos(position) }
    chunks.forEach { (chunkPos, voxelPositions) ->
        voxelEvent(
            chunkPos,
            VoxelUpdate.DetachDecal(layers),
            VoxelEvent.Selector.Multi(voxelPositions)
        )
    }
}


fun World.attachDecal(
    layer: DecalsLayerType,
    contents: DecalContents,
    direction: Direction,
    pos: Pos,
    voxelPos: VoxelPos
)  {
    singleBlockVoxelEvent(
        voxelPos,
        VoxelUpdate.AttachDecal(
            direction,
            projectedDecal(contents, direction, pos, voxelPos),
            layer
        )
    )
}

fun World.setDecals(voxelPos: VoxelPos, decals: BlockDecals?) {
    singleBlockVoxelEvent(
        voxelPos,
        VoxelUpdate.Set(
            decals = decals?.let { Setter.Set(it) } ?: Setter.Remove()
        )
    )
}

fun projectedDecal(
    contents: DecalContents,
    direction: Direction,
    pos: Pos,
    voxelPos: VoxelPos,
): Decal {
    val localX = pos.x - voxelPos.x
    val localY = pos.y - voxelPos.y
    val localZ = pos.z - voxelPos.z

    val (u, v) = when (direction) {
        Direction.UP    -> localX to localZ
        Direction.DOWN  -> localX to localZ

        Direction.NORTH -> localX to localY
        Direction.SOUTH -> localX to localY

        Direction.WEST  -> localZ to localY
        Direction.EAST  -> localZ to localY
    }

    val depth = when (direction) {
        Direction.DOWN  -> localY
        Direction.UP    -> 1f - localY

        Direction.NORTH -> localZ
        Direction.SOUTH -> 1f - localZ

        Direction.WEST  -> localX
        Direction.EAST  -> 1f - localX
    }

    return Decal(
        floorToInt(u * 16),
        floorToInt(v * 16),
        depth,
        contents
    )
}