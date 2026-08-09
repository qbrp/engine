package org.lain.engine.server

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.player.has
import org.lain.engine.storage.PersistentId
import org.lain.engine.world.Location
import org.lain.engine.world.World

data class TrackingState(
    val synced: MutableSet<PersistentId> = mutableSetOf(),
    val fresh: MutableSet<PersistentId> = mutableSetOf()
) {
    fun update(entities: Set<PersistentId>) {
        fresh.clear()
        synced.retainAll(entities)
        entities.forEach { entity ->
            if (synced.add(entity)) {
                fresh.add(entity)
            }
        }

    }
}

data class PlayerSyncState(
    val trackingPlayers: MutableMap<EntityId, LocationedPlayer> = mutableMapOf(),
    val freshPlayers: MutableSet<LocationedPlayer> = mutableSetOf(),
    val entities: TrackingState = TrackingState(),
    var isWorldSynced: Boolean = false,
    var confirmed: Boolean = false
) : Component

fun World.tickPlayerTrackingSystem(server: EngineServer, desynchronizationRadius: Int) {
    val squaredDesynchronizationRadius = desynchronizationRadius * desynchronizationRadius
    iterate<Location, Interests, PlayerSyncState>()
        { player, (position), interests, syncState ->
            val (trackingPlayers, freshPlayers, entities, voxels) = syncState
            if ((server.isReplay && !player.hasComponent<ReplayViewer>()) || player.hasComponent<PlayerInstantiationConfirmation>()) {
                syncState.confirmed = false
                return@iterate
            }

            freshPlayers.clear()

            val playersOutRadius = trackingPlayers.values.toMutableList()
            val frame = interests.frame
            frame.interestPlayers.forEach { locationedInterestPlayer ->
                val (interestPlayer, _) = locationedInterestPlayer
                playersOutRadius -= locationedInterestPlayer
                trackingPlayers.getOrPut(interestPlayer.entity) {
                    freshPlayers += locationedInterestPlayer
                    locationedInterestPlayer
                }
            }
            playersOutRadius.forEach { (player, outRadiusLocation) ->
                if (player.destroyed || outRadiusLocation.position.squaredDistanceTo(position) > squaredDesynchronizationRadius) {
                    trackingPlayers.remove(player.entity)
                }
            }

            entities.update(frame.interestEntities + frame.interestVoxels)
        }
}