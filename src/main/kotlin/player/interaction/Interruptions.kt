package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.world.World

object InteractionInterrupt : Component

data class UsingInput(val input: InputAction) : Component

data class UsingItem(val item: EntityId) : Component

fun World.tickInterruptionsSystem() {
    iterate<PlayerInput, PlayerInventory, UsingInput> { entity, input, inventory, using ->
        if (using.input !in input.actions) {
            entity.setComponent(InteractionInterrupt)
            inventory.mainHandInteractor.setComponent(InteractionInterrupt)
            inventory.offHandInteractor.setComponent(InteractionInterrupt)
            entity.removeComponent<UsingInput>()
        }
    }
    iterate<Hand, UsingItem>() { interactor, hand, (usedItem) ->
        if (hand.item != usedItem) {
            interactor.setComponent(InteractionInterrupt)
            interactor.removeComponent<UsingItem>()
        }
    }
}