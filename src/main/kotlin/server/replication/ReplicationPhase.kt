package org.lain.engine.server.replication

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.server.EngineServer
import org.lain.engine.world.World

data class PlayerInstantiationConfirmation(
    var timeout: Int = 8_000
) : Component

@Serializable
object Networked : Component

fun World.tickReplicationSystem(server: EngineServer) = runBlocking {
    val globals = server.globals
    val synchronizationRadius = globals.playerSynchronizationRadius
    val desynchronizationRadius = synchronizationRadius + globals.playerDesynchronizationThreshold
    val frame = componentManager.withoutThreadRestriction {
        withContext(Dispatchers.Default) {
            launch { tickPlayerInterestsSystem(synchronizationRadius) }
            async { captureReplicationDelta() }
        }
    }
    tickPlayerTrackingSystem(server, desynchronizationRadius)
    sendReplicationPackets(frame.await(), server.handler)
}
