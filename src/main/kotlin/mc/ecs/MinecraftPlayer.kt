package org.lain.engine.mc.ecs

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.mc.BlockStateVoxelMeta
import org.lain.engine.mc.McGameModes
import org.lain.engine.mc.bodyHeight
import org.lain.engine.mc.enginePlayerAccess
import org.lain.engine.mc.getText
import org.lain.engine.mc.isOp
import org.lain.engine.mc.pitch
import org.lain.engine.mc.previousGameMode
import org.lain.engine.mc.set
import org.lain.engine.mc.toMinecraft
import org.lain.engine.mc.yaw
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.EnginePlayerModel
import org.lain.engine.player.GiveItemEvent
import org.lain.engine.player.Orientation
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.PlayerMode
import org.lain.engine.player.PlayerModeComponent
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.StartSpectatingMark
import org.lain.engine.player.Velocity
import org.lain.engine.player.attributes
import org.lain.engine.player.displayName
import org.lain.engine.player.get
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.player.require
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.SBool
import org.lain.engine.world.Location
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.World
import kotlin.streams.asSequence

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

val GameType.enginePlayerMode
    get() = when (this) {
        GameType.SURVIVAL -> PlayerMode.DEFAULT
        GameType.ADVENTURE -> PlayerMode.DEFAULT
        GameType.CREATIVE -> PlayerMode.GM
        GameType.SPECTATOR -> PlayerMode.SPECTATOR
    }

val Player.enginePlayerMode
    get() = when {
        isSpectator -> PlayerMode.SPECTATOR
        isCreative -> PlayerMode.GM
        else -> PlayerMode.DEFAULT
    }

fun World.synchronizeMinecraftPlayerGameMode() {
    iterate<MinecraftPlayer, PlayerModeComponent>() { entity, player, modeComponent ->
        val spawnMark = entity.removeComponent<SpawnMark>()
        val spectatorMark = entity.removeComponent<StartSpectatingMark>()
        val previousGameMode = player.previousGameMode

        if (spectatorMark != null && !player.entity.isSpectator) {
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

        velocity.prev.set(velocity.motion)
        location.position.set(position)
        velocity.motion.set(entity.deltaMovement)

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

private fun World.tickMinecraftPlayerInputSyncSystem() {
    iterate<MinecraftPlayer, PlayerInput> { _, minecraftPlayer, input ->
        input.isSprinting = minecraftPlayer.entity.isSprinting
    }
}

private fun World.tickMinecraftPlayerModeSyncSystem() {
    iterate<MinecraftPlayer, PlayerModeComponent> { _, minecraftPlayer, mode ->
        mode.mode = minecraftPlayer.entity.enginePlayerMode
    }
}

private fun World.tickMinecraftPlayerPhysics() {
    iterate<MinecraftPlayer, PlayerPhysics> { _, (minecraftPlayer), physics ->
        physics.collides.clear()
        physics.collides.addAll(
            minecraftPlayer.level()
            .getBlockStates(minecraftPlayer.boundingBox)
            .map { BlockStateVoxelMeta(it) }
            .toList()
        )
    }
}

private fun World.tickMinecraftPlayerAccess() {
    iterate<MinecraftPlayer, PlayerComponent>() { entity, minecraft, _ ->
        val playerAccess = minecraft.entity.enginePlayerAccess
        val data = playerAccess.data
        val attributes = entity.requireComponent<PlayerAttributes>()
        val customAttributes = entity.requireComponent<CustomPlayerAttributes>()
        val model = entity.requireComponent<EnginePlayerModel>()
        val physics = entity.requireComponent<PlayerPhysics>()
        data.updateThreadSafe(entity.displayName().getText())
        data.scale = model.scale
        data.speed = customAttributes.speed ?: attributes.speed
        data.jumpStrength = customAttributes.jumpStrength ?: attributes.jumpStrength
        data.noPhysics = physics.noClip
        data.flyingSpeed = attributes.flySpeed
        data.canJump = attributes.jumpStrength > 0f
        data.initialized = true

        if (playerAccess.consumeJump()) {
            val jump = simulation.scriptEngine.createScriptComponent(
                SBool(true),
                CoreScriptComponents.PLAYER_JUMP
            )
            entity.setComponent(jump, CoreScriptComponents.PLAYER_JUMP)
        }
    }
}

fun World.tickMinecraftPlayerSyncSystem() {
    tickMinecraftPlayerPhysics()
    tickMinecraftPlayerMotionSyncSystem()
    tickMinecraftPlayerOrientationSyncSystem()
    tickMinecraftPlayerModelSyncSystem()
    tickMinecraftPlayerInputSyncSystem()
    tickMinecraftPlayerModeSyncSystem()
    tickMinecraftPlayerAccess()
}
