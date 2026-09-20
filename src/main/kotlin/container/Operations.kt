package org.lain.engine.container

import org.lain.cyberia.ecs.*
import org.lain.engine.item.*
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getDebugName
import org.lain.engine.world.World

data class TransferOperation(
    val item: EngineItem,
    val container: EntityId,
) : Component

object Unresolved : Component

object Approved : Component

data class Rejected(val reason: String) : Component

data class MoveFrom(val container: EntityId) : Component

context(world: World)
fun ContainerEntity.transferOperation(item: EngineItem): EntityId {
    val event = world.emitEvent(
        TransferOperation(item, entity)
    )
    event.setComponent(Unresolved)
    return event
}

fun World.approveUnresolvedTransferOperation() {
    iterate<TransferOperation, Unresolved>() { operation, _, _ ->
        if (!operation.hasComponent<Rejected>()) {
            operation.setComponent(Approved)
        }
    }
}

fun World.setupTransferOperationMoveFrom() {
    // если предмет уже содержится в контейнере, маркируем (причем, не важно - в том же или другом)
    iterate<TransferOperation, Approved>() { operation, (item), _ ->
        val oldContainer = item.getComponent<ContainedIn>()
        if (oldContainer != null) {
            operation.setComponent(MoveFrom(oldContainer.container))
        }
    }
}

fun World.tickTransformOperationRejectLogSystem() {
    iterate<TransferOperation, Rejected>() { _, (item, container), (reason) ->
        containerSystemLogger.warn(
            "Операция перемещения предмета ${item.getDebugName()} в контейнер ${container.getDebugName()} была отклонена: $reason"
        )
    }
}