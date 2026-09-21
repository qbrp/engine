package org.lain.engine.server.replication

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerComponent
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.ChunkedPos
import org.lain.engine.world.DynamicVoxelInterest
import org.lain.engine.world.EngineChunkPos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.location

data class LocationedPlayer(val player: EnginePlayer, val location: Location)

data class LocationedVoxel(val id: PersistentId, val chunkPos: EngineChunkPos)

data class Interests(var frame: Frame = Frame()) : Component {
    data class Frame(
        val interestEntities: Set<PersistentId> = setOf(),
        val interestVoxels: Set<LocationedVoxel> = setOf(),
        val interestPlayers: Set<LocationedPlayer> = setOf(),
    )
}

fun World.tickPlayerInterestsSystem(
    synchronizationRadius: Int,
) {
    val world = this
    val squaredSynchronizationRadius = synchronizationRadius * synchronizationRadius
    iterate<PlayerComponent, Interests, Location>() { _, (player), interests, location ->
        val position = location.position
        val playersInRadius = filterNearestPlayers(world, position, synchronizationRadius, players)
            .filter { it != player }
        val entitiesInRadius = mutableSetOf<PersistentId>()
        val voxelsInRadius = mutableSetOf<LocationedVoxel>()

        world.iterate<Networked, Location, PersistentIdComponent>() { entity, _, (entityPosition), (persistentId) ->
            if (entityPosition.squaredDistanceTo(position) < squaredSynchronizationRadius) {
                if (entity.hasComponent<DynamicVoxelInterest>()) {
                    voxelsInRadius += LocationedVoxel(
                        persistentId,
                        entity.requireComponent<ChunkedPos>().pos,
                    )
                } else {
                    entitiesInRadius += persistentId
                }
            }
        }

        interests.frame = Interests.Frame(
            entitiesInRadius,
            voxelsInRadius,
            playersInRadius.map {
                LocationedPlayer(it, it.location)
            }.toSet()
        )
    }
}
