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
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Orientation
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerMode
import org.lain.engine.player.PlayerModeComponent
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.StartSpectatingMark
import org.lain.engine.player.Velocity
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.world.Location
import org.lain.engine.world.World

data class EngineItemStack(val engineItem: EngineItem, val itemStack: ItemStack)

data class MinecraftPlayer(
    var entity: Player,
    var itemStacks: Sequence<EngineItemStack> = emptySequence(),
    var previousGameMode: GameType? = null,
    var setGameMode: GameType? = null,
) : Component

val EnginePlayer.minecraftEntity
    get() = require<MinecraftPlayer>().entity

val EnginePlayer.minecraftEntityNullable
    get() = get<MinecraftPlayer>()?.entity

fun World.tickMinecraftPlayerInventorySystem() {
    iterate<MinecraftPlayer, PlayerInventory> { _, (entity, itemStacks), inventory ->
        val mainItemStack = entity.mainHandItem
        val offItemStack = entity.offhandItem
        var mainHandItem: EngineItem? = null
        var offHandItem: EngineItem? = null
        val remainingPlayerInventoryItems = inventory.items.toMutableSet()

        for ((item, itemStack) in itemStacks) {
            if (mainItemStack?.engineItem() == item) {
                mainHandItem = item
            }
            if (offItemStack?.engineItem() == item) {
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
        inventory.selectedSlot = entity.inventory.selectedSlot

        for (removedItem in remainingPlayerInventoryItems) {
            if (removedItem != inventory.cursorItem) {
                inventory.items.remove(removedItem)
            }
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

private fun World.tickMinecraftPlayerMotionSyncSystem() {
    iterate<MinecraftPlayer, Location, Velocity> { _, minecraftPlayer, location, velocity ->
        val entity = minecraftPlayer.entity
        val position = entity.position()

        velocity.prev.set(position)
        location.position.set(position)
        velocity.motion.set(
            location.position.x - velocity.prev.x,
            location.position.y - velocity.prev.y,
            location.position.z - velocity.prev.z
        )

        val setVelocity = velocity.set
        if (setVelocity != null) {
            entity.deltaMovement = setVelocity.toMinecraft()
            velocity.set = null
        }
    }
}

private fun World.tickMinecraftPlayerOrientationSyncSystem() {
    iterate<MinecraftPlayer, Orientation> { _, minecraftPlayer, orientation ->
        val entity = minecraftPlayer.entity

        entity.yaw += orientation.translationYaw
        orientation.translationYaw = 0f
        entity.pitch += orientation.translationPitch
        orientation.translationPitch = 0f

        orientation.yaw = entity.yaw
        orientation.pitch = entity.pitch
    }
}

private fun World.tickMinecraftPlayerModelSyncSystem() {
    iterate<MinecraftPlayer, EnginePlayerModel> { _, minecraftPlayer, model ->
        val entity = minecraftPlayer.entity

        model.standingEyeHeight = entity.eyeHeight
        model.height = entity.bodyHeight * model.scale
        if (model.lastTickScale != model.scale) {
            model.lastTickScale = model.scale
            entity.refreshDimensions()
        }
    }
}

private fun World.tickMinecraftPlayerMovementStatusSyncSystem() {
    iterate<MinecraftPlayer, MovementStatus> { _, minecraftPlayer, status ->
        status.isSprinting = minecraftPlayer.entity.isSprinting
    }
}

private fun World.tickMinecraftPlayerModeSyncSystem() {
    iterate<MinecraftPlayer, PlayerModeComponent> { _, minecraftPlayer, mode ->
        mode.mode = minecraftPlayer.entity.enginePlayerMode
    }
}

fun World.tickMinecraftPlayerSyncSystem() {
    tickMinecraftPlayerMotionSyncSystem()
    tickMinecraftPlayerOrientationSyncSystem()
    tickMinecraftPlayerModelSyncSystem()
    tickMinecraftPlayerMovementStatusSyncSystem()
    tickMinecraftPlayerModeSyncSystem()
}
