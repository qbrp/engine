package org.lain.engine.mc

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Orientation
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerMode
import org.lain.engine.player.PlayerModeComponent
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.StartSpectatingMark
import org.lain.engine.player.Velocity
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.Location
import org.lain.engine.world.World

data class EngineItemStack(val engineItem: EngineItem, val itemStack: ItemStack)

data class MinecraftPlayer(
    val entity: Player,
    var previousGameMode: GameType? = null,
    var setGameMode: GameType? = null,
) : Component

context(world: World)
fun synchronizeMinecraftPlayerInventory(
    player: EntityId,
    items: Sequence<EngineItemStack>,
    minecraftPlayer: Player,
) {
    val inventory = player.getComponent<PlayerInventory>() ?: return
    val mainItemStack = minecraftPlayer.mainHandItem
    val offItemStack = minecraftPlayer.offhandItem
    var mainHandItem: EngineItem? = null
    var offHandItem: EngineItem? = null
    val remainingPlayerInventoryItems = inventory.items.toMutableSet()

    for ((item, itemStack) in items) {
        if (mainItemStack?.engineItem(world) == item) {
            mainHandItem = item
        }
        if (offItemStack?.engineItem(world) == item) {
            offHandItem = item
        }

        inventory.items += item
        remainingPlayerInventoryItems -= item

        val minecraftItem = item.getComponent<MinecraftItem>()
        if (minecraftItem == null) {
            item.setComponent(MinecraftItem(mutableListOf(itemStack)))
        } else {
            minecraftItem.itemStacks += itemStack
        }
    }

    inventory.mainHandFree = mainItemStack.isEmpty
    inventory.mainHandItem = mainHandItem
    inventory.offHandItem = offHandItem
    inventory.selectedSlot = minecraftPlayer.inventory.selectedSlot

    for (removedItem in remainingPlayerInventoryItems) {
        if (removedItem != inventory.cursorItem) {
            inventory.items.remove(removedItem)
        }
    }
}

val GameType.enginePlayerMode
    get() = when(this) {
        GameType.SURVIVAL -> PlayerMode.DEFAULT
        GameType.ADVENTURE -> PlayerMode.DEFAULT
        GameType.CREATIVE -> PlayerMode.GM
        GameType.SPECTATOR -> PlayerMode.SPECTATOR
    }

val Player.enginePlayerMode
    get() = (this.gameMode() ?: GameType.DEFAULT_MODE).enginePlayerMode

context(world: World)
fun synchronizeMinecraftPlayerState(
    player: EntityId,
    minecraftPlayer: Player,
) {
    val location = player.getComponent<Location>()
    val velocity = player.getComponent<Velocity>()
    if (location != null && velocity != null) {
        val pos = minecraftPlayer.position()
        velocity.prev.set(location.position)
        location.position.set(pos)

        velocity.motion.set(
            location.position.x - velocity.prev.x,
            location.position.y - velocity.prev.y,
            location.position.z - velocity.prev.z
        )

        val setVelocity = velocity.set
        if (setVelocity != null) {
            minecraftPlayer.deltaMovement = setVelocity.toMinecraft()
            velocity.set = null
        }
    }

    player.getComponent<Orientation>()?.let { orientation ->
        minecraftPlayer.yaw += orientation.translationYaw
        orientation.translationYaw = 0f
        minecraftPlayer.pitch += orientation.translationPitch
        orientation.translationPitch = 0f

        orientation.yaw = minecraftPlayer.yaw
        orientation.pitch = minecraftPlayer.pitch
    }

    player.getComponent<EnginePlayerModel>()?.let { model ->
        model.standingEyeHeight = minecraftPlayer.eyeHeight
        model.height = minecraftPlayer.bodyHeight * model.scale
        if (model.lastTickScale != model.scale) {
            model.lastTickScale = model.scale
            minecraftPlayer.refreshDimensions()
        }
    }

    player.getComponent<MovementStatus>()?.let { status ->
        status.isSprinting = minecraftPlayer.isSprinting
    }
    player.getComponent<PlayerModeComponent>()?.mode = minecraftPlayer.enginePlayerMode
}

fun World.synchronizeMinecraftPlayerGameMode() {
    iterate<MinecraftPlayer, PlayerModeComponent>() { entity, player, modeComponent ->
        val spawnMark = entity.removeComponent<SpawnMark>()
        val spectatorMark = entity.removeComponent<StartSpectatingMark>()
        val gameMode = player.entity.gameMode()
        val previousGameMode = player.previousGameMode

        if (spectatorMark != null && gameMode != GameType.SPECTATOR) {
            player.setGameMode = McGameModes.SPECTATOR
        }

        if (spawnMark != null) {
            player.setGameMode =
                previousGameMode ?: when (player.entity.isOp) {
                    false -> McGameModes.SURVIVAL
                    true -> McGameModes.CREATIVE
                }
        }

        modeComponent.mode = player.entity.enginePlayerMode
    }
}

fun World.applyServerMinecraftPlayerGameMode() {
    iterate<MinecraftPlayer> { _, player ->
        val serverPlayer = player.entity as? ServerPlayer ?: return@iterate
        val newGameMode = player.setGameMode
        if (newGameMode != null) {
            serverPlayer.setGameMode(newGameMode)
            player.setGameMode = null
        }
        player.previousGameMode = serverPlayer.previousGameMode
    }
}

fun World.synchronizeMinecraftPlayerState() {
    iterate<MinecraftPlayer>() { entity, minecraftPlayer ->
        synchronizeMinecraftPlayerState(entity, minecraftPlayer.entity)
    }
}
