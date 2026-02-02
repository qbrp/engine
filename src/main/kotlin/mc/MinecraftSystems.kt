package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.apache.logging.log4j.core.jmx.Server
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.EngineItem
import org.lain.engine.item.GunEvent
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.engine
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
import org.lain.engine.util.applyIfExists
import org.lain.engine.util.engineId
import org.lain.engine.util.get
import org.lain.engine.util.has
import org.lain.engine.util.remove
import org.lain.engine.util.require
import org.lain.engine.util.set
import net.minecraft.world.World as McWorld
import org.lain.engine.world.Location
import org.lain.engine.world.Orientation
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
    val items = mutableListOf<ItemUuid>()
    for (stack in entity.inventory.mainStacks) {
        val engineItem = stack.engine() ?: continue
        val itemUuid = engineItem.uuid
        if (itemUuid != null) {
            if (items.contains(itemUuid)) {
                stack.remove(EngineItemReferenceComponent.TYPE)
                engineServer.wrapItemStack(player, engineItem.id, stack)
            } else {
                items.add(itemUuid)
            }
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
        val items = itemStacks.mapNotNull { itemStack ->
            val reference = itemStack.get(EngineItemReferenceComponent.TYPE) ?: return@mapNotNull null
            val item = reference.getItem() ?: run {
                if (reference.version != 0) {
                    detachEngineItemStack(itemStack)
                }
                return@mapNotNull null
            }
            item to itemStack
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