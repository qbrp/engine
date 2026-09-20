package org.lain.engine.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.data.ComponentSnapshot
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.snapshot
import org.lain.engine.player.interaction.ActionSyncEvent
import org.lain.engine.player.interaction.InteractionId
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.world.World

@Serializable
sealed interface ReplicationSnapshot {
    @Serializable
    @SerialName("component")
    data class Component(
        val component: ComponentSnapshot,
    ) : ReplicationSnapshot

    @Serializable
    @SerialName("action_sync")
    data class ActionSync(
        val entity: PersistentId,
        val action: ComponentSnapshot,
        val interactionId: InteractionId,
    ) : ReplicationSnapshot

    @Serializable
    @SerialName("entity_rpc_receiver")
    data object EntityRpcReceiver : ReplicationSnapshot
}

context(world: World)
fun Component.replicationSnapshot(): ReplicationSnapshot {
    return when (this) {
        is ActionSyncEvent -> ReplicationSnapshot.ActionSync(
            entity.requireComponent<PersistentIdComponent>().id,
            action.snapshot(),
            interactionId
        )
        is EntityRpcReceiver -> ReplicationSnapshot.EntityRpcReceiver
        else -> ReplicationSnapshot.Component(snapshot())
    }
}