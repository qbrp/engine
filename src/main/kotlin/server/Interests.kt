package org.lain.engine.server

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.Player
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.math.filterNearestPlayers
import org.lain.engine.world.DynamicVoxelInterest
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.location

data class LocationedPlayer(val player: EnginePlayer, val location: Location)

data class Interests(
    var frame: Frame = Frame(),
) : Component {
    data class Frame(
        val interestEntities: Set<PersistentId> = setOf(),
        val interestVoxels: Set<PersistentId> = setOf(), //возможно стоит убрать
        val interestPlayers: Set<LocationedPlayer> = setOf(),
    )
}

fun World.tickPlayerInterestsSystem(
    synchronizationRadius: Int,
) {
    val world = this
    val squaredSynchronizationRadius = synchronizationRadius * synchronizationRadius
    iterate<Player, Interests, Location>() { _, (player), interests, location ->
        val position = location.position
        val playersInRadius = filterNearestPlayers(world, position, synchronizationRadius, players)
            .filter { it != player }
        val entitiesInRadius = mutableSetOf<PersistentId>()
        val voxelsInRadius = mutableSetOf<PersistentId>()

        world.iterate<Networked, Location, PersistentIdComponent>() { entity, _, (entityPosition), (persistentId) ->
            if (entityPosition.squaredDistanceTo(position) < squaredSynchronizationRadius) {
                if (entity.hasComponent<DynamicVoxelInterest>()) {
                    voxelsInRadius += persistentId
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