package org.lain.engine.mc

import net.minecraft.IdentifierException
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntitySelector
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import org.lain.cyberia.ecs.require
import org.lain.engine.CommonEngineServerMod
import org.lain.engine.player.*
import org.lain.engine.script.Callbacks
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.EngineServer
import org.lain.engine.util.injectMinecraftEngineServer
import org.lain.engine.util.math.*
import org.lain.engine.world.*

fun MinecraftUsername(player: Player) = Username(player.name.string)

val Player.engineId
    get() = PlayerId(this.uuid)

fun Pos.toBlockPos(): BlockPos = BlockPos.containing(asVec3().toMinecraft())

fun chunkSquare(from: ChunkPos, size: Int): Array<ChunkPos> {
    return Array(size * size) { i ->
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
        val results = ProjectileUtil.getEntityHitResult(
            entity1,
            entity1.eyePosition,
            entity1.eyePosition.add(entity1.lookAngle.scale(distance.toDouble())),
            entity1.boundingBox
                .expandTowards(entity1.lookAngle.scale(distance.toDouble()))
                .inflate(1.0),
            EntitySelector.CAN_BE_PICKED,
            distance.toDouble(),
        );
        return (results?.entity as? Player?)?.let {
            (table as EntityTable.Entity2PlayerTable<Player>).getPlayer(it)
        }
    }

    override fun canSee(player: EnginePlayer, voxelPos: VoxelPos, isClient: Boolean): Boolean {
        val table = if (isClient) playerTable.client else playerTable.server
        val entity = table.getEntity(player) ?: return false
        val blockPos =BlockPos(voxelPos.x, voxelPos.y, voxelPos.z)
        val context = ClipContext(
            entity.eyePosition,
            blockPos.center,
            ClipContext.Block.VISUAL,
            ClipContext.Fluid.WATER,
            CollisionContext.empty()
        )
        val raycastResult = entity.level().clip(context)
        return raycastResult != null && (raycastResult.type == HitResult.Type.MISS || raycastResult.blockPos == blockPos)
    }
}

// Payers

val Player.selectedStack: ItemStack?
    get() = containerMenu.carried

val MinecraftServer.players
    get() = playerList.players

fun MinecraftServer.getPlayer(id: PlayerId) = playerList.getPlayer(id.value)

var Player.yaw
    get() = yRot
    set(value) { yRot = value }

var Player.pitch
    get() = xRot
    set(value) { xRot = value }

val Entity.bodyHeight
    get() = bbHeight

val ServerPlayer.currentGameMode
    get() = this.gameMode.gameModeForPlayer

val ServerPlayer.previousGameMode
    get() = this.gameMode.previousGameModeForPlayer


object McGameModes {
    val CREATIVE = GameType.CREATIVE
    val SPECTATOR = GameType.SPECTATOR
    val SURVIVAL = GameType.SURVIVAL
}

val Player.ownedItems get() = inventory.iterator().asSequence().toList()

val Player.visibleInventoryItems: Set<ItemStack>
    get() {
        return (containerMenu.items + ownedItems).toSet()
    }

val Player.carriedItem get() = containerMenu.carried

fun Player.sendActionBarMessage(messageMm: String) {
    displayClientMessage(messageMm.parseMiniMessage(), true)
}

fun Player.sendMessage(messageMm: String) {
    displayClientMessage(messageMm.parseMiniMessage(), false)
}

val EnginePlayer.displayNameMiniMessage
    get() = this.require<DisplayName>().let { it.custom?.textMiniMessage ?: it.username.value }

val CustomName.textMiniMessage
    get() = "<gradient:#${color1.hexString()}:#${(color2 ?: color1).hexString()}>$string</gradient>"

fun EngineServer.getWorld(world: Level): World {
    return getWorld(world.engine)
}

// ID

fun vanillaId(id: String) = Identifier.withDefaultNamespace(id)

fun engineId(path: String) = Identifier.fromNamespaceAndPath(CommonEngineServerMod.MOD_ID, path)!!

fun isIdPathValid(id: String) = Identifier.isValidPath(id)

fun InvalidIdException(id: String) = IdentifierException("Non [a-z0-9/._-] character in path of location: $id")

fun parseId(str: String) = Identifier.parse(str)

fun <T : Any> registryOf(key: ResourceKey<Registry<T>>): Registry<T> {
    val server by injectMinecraftEngineServer()
    return server.minecraftServer.registryAccess().get(key).get().value()
}

val ResourceKey<*>.path
    get() = identifier().path

val ResourceKey<*>.idString
    get() = identifier().toString()

typealias McIdentifier = Identifier

fun <T : Any> registerDataComponentType(
    id: String,
    statement: DataComponentType.Builder<T>.() -> Unit
) = Registry.register(
    BuiltInRegistries.DATA_COMPONENT_TYPE,
    engineId(id),
    DataComponentType
        .builder<T>()
        .apply(statement)
        .build()
)

val Level.engine
    get() = WorldId(this.dimensionTypeRegistration().registeredName)

// MATH

typealias MathMc = Mth

fun ChunkPos.engineChunkPos() = EngineChunkPos(x, z)

fun BlockPos.voxelPos() = VoxelPos(this.x, this.y, this.z)

fun MutableEVec3.set(vec3: Vec3) {
    this.x = vec3.x.toFloat()
    this.y = vec3.y.toFloat()
    this.z = vec3.z.toFloat()
}

fun Direction.engine() = when(this) {
    Direction.DOWN -> EDirection.DOWN
    Direction.UP -> EDirection.UP
    Direction.NORTH -> EDirection.NORTH
    Direction.SOUTH -> EDirection.SOUTH
    Direction.WEST -> EDirection.WEST
    Direction.EAST -> EDirection.EAST
}

fun EVec3.toMinecraft(): Vec3 = Vec3(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3.engine(): EVec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

// TEXT

typealias Text = Component

fun literalText(text: String) = Component.literal(text)
fun literalTextNullable(text: String?) = text?.let { literalText(it) } ?: Text.EMPTY

// BLOCKS

val BlockState.registryKey
    get() = block.builtInRegistryHolder().key()

fun MutableBlockPos() = BlockPos.MutableBlockPos()

fun VoxelPos.asLong(): Long = BlockPos.asLong(x, y, z)

val ChunkAccess.startX get() = this.pos.minBlockX
val ChunkAccess.startZ get() = this.pos.minBlockZ
val ChunkAccess.endX get() = this.pos.maxBlockX
val ChunkAccess.endZ get() = this.pos.maxBlockZ

fun Callbacks.executePlaceVoxelCallback(player: EnginePlayer?, world: World, pos: VoxelPos, state: BlockState) {
    placeVoxel.execute(
        ScriptContext.VoxelAction(
            player,
            world,
            pos,
            object : VoxelMeta {
                override val id: String
                    get() = state.registryKey.idString

                override fun hasTag(id: String): Boolean {
                    return state.`is`(blockTag(id))
                }
            }
        )
    )
}

fun blockTag(rawId: String) = blockTag(parseId(rawId))

fun blockTag(id: Identifier) = TagKey.create(Registries.BLOCK, id)

fun MutableVoxelPos.Companion.ofLong(long: Long) = BlockPos.of(long).let { MutableVoxelPos(it.x, it.y, it.z) }

fun blockRegistryEntryExists() = Registries.BLOCK