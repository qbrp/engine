package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.Count
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.count
import org.lain.engine.player.DestroyItemSignal
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerModel
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.StartSpectatingMark
import org.lain.engine.player.Username
import org.lain.engine.player.isInGameMasterMode
import org.lain.engine.player.isSpectating
import org.lain.engine.server.EngineServer
import org.lain.engine.util.Vec3
import org.lain.engine.util.apply
import org.lain.engine.util.get
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.util.set
import net.minecraft.world.World as McWorld
import org.lain.engine.world.Location
import org.lain.engine.player.Orientation
import org.lain.engine.player.OrientationTranslation
import org.lain.engine.player.cursorItem
import org.lain.engine.player.handItem
import org.lain.engine.player.items
import org.lain.engine.world.Velocity
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

fun Vec3.toMinecraft(): Vec3d = Vec3d(x.toDouble(), y.toDouble(), z.toDouble())

fun Vec3d.engine(): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

val McWorld.engine
    get() = WorldId(this.registryKey.value.toString())


fun EngineServer.getWorld(world: McWorld): World {
    return getWorld(world.engine)
}

fun EngineServer.getPlayerWorld(player: PlayerEntity): World {
    return getWorld(player.entityWorld)
}

fun Username(text: Text) = Username(text.string)

fun excludeEngineItemDuplicates(engineServer: EngineMinecraftServer, entity: ServerPlayerEntity, player: EnginePlayer) {
    val itemCounts = mutableMapOf<ItemUuid, Int>()
    val stackCounts = mutableMapOf<ItemStack, Int>()
    val cursorItem = player.cursorItem
    val stacks = entity.inventory.mainStacks
        .map { it to it.engine()?.getItem() }
        .filter { it.second != null }
    for ((stack, engineItem) in stacks) {
        val itemUuid = engineItem!!.uuid
        val int1 = itemCounts[itemUuid] ?: 0
        itemCounts[itemUuid] = int1 + engineItem.count
        val int2 = stackCounts[stack] ?: 0
        stackCounts[stack] = int2 + stack.count
    }

    for ((stack, engineItem) in stacks) {
        val count = itemCounts[engineItem!!.uuid] ?: continue
        val handItemCount = if (cursorItem?.uuid == engineItem.uuid) cursorItem.count else 0

        if (count - handItemCount != (stackCounts[stack] ?: continue)) {
            stack.remove(ENGINE_ITEM_REFERENCE_COMPONENT)
            engineServer.wrapItemStack(player, engineItem.id, stack)
        }
    }
}

fun updateServerMinecraftSystems(
    server: EngineMinecraftServer,
    table: ServerPlayerTable,
    players: List<EnginePlayer>,
) {
    val engine = server.engine
    for (player in players) {
        val entity = table.getEntity(player) ?: return
        val world = engine.getWorld(entity.entityWorld)

        val itemStacks = entity.inventory + entity.currentScreenHandler.stacks
        val items: MutableList<Pair<EngineItem, ItemStack>> = mutableListOf()

        for (itemStack in itemStacks) {
            var item: EngineItem? = null
            val reference = itemStack.engine()
            if (reference != null) {
                item = reference.getItem()
                if (item == null && reference.version != 0) {
                    detachEngineItemStack(itemStack)
                }
            }

            val instantiate = itemStack.remove(ENGINE_ITEM_INSTANTIATE_COMPONENT)
            if (reference == null && instantiate != null) {
                item = server.wrapItemStack(player, ItemId(instantiate), itemStack)
            }

            if (item != null) {
                items.add(item to itemStack)
            }
        }

        updatePlayerMinecraftSystems(player, items, entity, world)
        if (entity is ServerPlayerEntity) {
            excludeEngineItemDuplicates(server, entity, player)
        }
    }
}

fun updatePlayerMinecraftSystems(
    player: EnginePlayer,
    items: List<Pair<EngineItem, ItemStack>>,
    entity: PlayerEntity,
    world: World
) {
    val pos = entity.entityPos
    player.apply<Location> {
        position.set(pos)
        this.world = world
    }

    player.apply<OrientationTranslation> {
        if (yaw != 0f) {
            entity.yaw += yaw
            yaw = 0f
        }
        if (pitch != 0f) {
            entity.pitch += pitch
            pitch = 0f
        }
    }

    player.apply<Orientation> {
        yaw = entity.yaw
        pitch = entity.pitch
    }

    player.apply<Velocity> {
        motion.set(entity.movement)
    }

    player.apply<PlayerModel> {
        scale = entity.scale
        standingEyeHeight = entity.standingEyeHeight
        height = entity.height * scale
    }

    player.apply<MovementStatus> {
        isSprinting = entity.isSprinting
    }

    if (entity is ServerPlayerEntity) {
        val hasSpawnMark = player.has<SpawnMark>()
        val hasSpectatorMark = player.has<StartSpectatingMark>()
        val interactionManager = entity.interactionManager
        val gameMode = interactionManager.gameMode
        val previousGameMode = interactionManager.previousGameMode

        if (hasSpectatorMark && gameMode != GameMode.SPECTATOR) {
            entity.changeGameMode(GameMode.SPECTATOR)
        }

        if (hasSpawnMark) {
            entity.changeGameMode(previousGameMode ?: entity.resolveGameMode())
        }

        if (hasSpawnMark) player.remove<SpawnMark>()
        if (hasSpectatorMark) player.remove<StartSpectatingMark>()
    }

    player.isInGameMasterMode = entity.isCreative
    player.isSpectating = entity.isSpectator

    val playerInventory = player.require<PlayerInventory>()
    val playerInventoryItems = playerInventory.items.toMutableList()
    val destroyItemSignal = player.get<DestroyItemSignal>()
    var handItemSet: EngineItem? = null

    for ((item, itemStack) in items) {
        if (entity.mainHandStack.engine()?.id == item.id) {
             handItemSet = item
        }

        playerInventory.items += item
        playerInventoryItems -= item

        updateEngineItemStack(itemStack, item)

        val countComponent = item.get<Count>()
        if (countComponent == null) {
            itemStack.count = 1
        } else {
            countComponent.value = itemStack.count
        }

        if (destroyItemSignal != null && destroyItemSignal.item == item.uuid) {
            itemStack.decrement(destroyItemSignal.count)
        }
    }

    playerInventory.handItem = handItemSet

    for (removedItem in playerInventoryItems) {
        playerInventory.items.remove(removedItem)
    }

    player.remove<DestroyItemSignal>()
}

private fun ServerPlayerEntity.resolveGameMode() = if (this.hasPermissionLevel(4)) {
    GameMode.CREATIVE
} else {
    GameMode.SURVIVAL
}