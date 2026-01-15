package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerModel
import org.lain.engine.player.SpawnMark
import org.lain.engine.player.StartSpectatingMark
import org.lain.engine.player.Username
import org.lain.engine.player.isInGameMasterMode
import org.lain.engine.player.isSpectating
import org.lain.engine.server.EngineServer
import org.lain.engine.util.Vec3
import org.lain.engine.util.apply
import org.lain.engine.util.has
import org.lain.engine.util.remove
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

fun updateServerMinecraftSystems(
    engine: EngineServer,
    table: ServerPlayerTable,
    players: List<Player>
) {
    for (player in players) {
        val entity = table.getEntity(player) ?: return
        val world = engine.getWorld(entity.entityWorld)

        updatePlayerMinecraftSystems(player, entity, world)
    }
}

fun updatePlayerMinecraftSystems(
    player: Player,
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
}

private fun ServerPlayerEntity.resolveGameMode() = if (this.hasPermissionLevel(4)) {
    GameMode.CREATIVE
} else {
    GameMode.SURVIVAL
}