package org.lain.engine.server.replication

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.data.PersistentId
import org.lain.engine.player.PlayerComponent
import org.lain.engine.server.EngineServer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World

data class PlayerInstantiationConfirmation(
    var timeout: Int = 8_000
) : Component

@Serializable
object Networked : Component

fun World.tickSynchronizationSystem(server: EngineServer) = runBlocking {
    val globals = server.globals
    val synchronizationRadius = globals.playerSynchronizationRadius
    val desynchronizationRadius = synchronizationRadius + globals.playerDesynchronizationThreshold
    val (job, frame) = componentManager.withoutThreadRestriction {
        withContext(Dispatchers.Default) {
            val job = launch { tickPlayerInterestsSystem(synchronizationRadius) }
            val frame = async { captureReplicationDelta() }
            job to frame
        }
    }
    job.join()
    tickPlayerTrackingSystem(server, desynchronizationRadius)
    sendReplicationPackets(frame.await(), server.handler)
}
