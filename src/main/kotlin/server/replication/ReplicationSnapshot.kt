package org.lain.engine.server.replication

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.data.ComponentSnapshot
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.snapshot
import org.lain.engine.player.Equipment
import org.lain.engine.player.EquipmentSlotId
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
            get() = org.lain.engine.script.EntityRpcReceiver.TYPE.id
    }

    @Serializable
    @SerialName("equipment")
    data class Equipment(val slots: Map<EquipmentSlotId, PersistentId>) : ReplicationSnapshot {
        override val id: String
            get() = org.lain.engine.player.Equipment.TYPE.id
    }
}

context(world: World)
fun Component.replicationSnapshot(): ReplicationSnapshot {
    return when (this) {
        is EntityRpcReceiver -> ReplicationSnapshot.EntityRpcReceiver
        is Equipment -> ReplicationSnapshot.Equipment(
            slots.mapValues { (_, entity) -> entity.requireComponent<PersistentIdComponent>().id }
        )
        else -> ReplicationSnapshot.Component(snapshot())
    }
}