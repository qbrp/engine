package org.lain.engine.server.replication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.engine.data.ComponentSnapshot
import org.lain.engine.data.snapshot
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.world.World

@Serializable
sealed interface ReplicationSnapshot {
    val id: String

    @Serializable
    @SerialName("component")
    data class Component(
        val component: ComponentSnapshot,
    ) : ReplicationSnapshot {
        override val id: String
            get() = component.id
    }

    @Serializable
    @SerialName("entity_rpc_receiver")
    data object EntityRpcReceiver : ReplicationSnapshot {
        override val id: String
            get() = componentTypeOf(org.lain.engine.script.EntityRpcReceiver::class).id
    }
}

context(world: World)
fun Component.replicationSnapshot(): ReplicationSnapshot {
    return when (this) {
        is EntityRpcReceiver -> ReplicationSnapshot.EntityRpcReceiver
        else -> ReplicationSnapshot.Component(snapshot())
    }
}