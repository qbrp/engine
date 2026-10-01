package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.world.World

object InteractionInterrupt : Component

data class UsingInput(val input: InputAction) : Component

data class UsingItem(val item: EntityId) : Component

fun World.tickInterruptionsSystem() {
    iterate<UsingItem>() { interactor, (usedItem) ->
        val inventory = interactor.getComponent<PlayerInventory>()
        val itemCandidates = setOfNotNull(
            interactor.getComponent<Hand>()?.item,
            inventory?.mainHandItem,
            inventory?.offHandInteractor,
        )

        if (itemCandidates.none { it == usedItem }) {
            interactor.setComponent(InteractionInterrupt)
            interactor.removeComponent<UsingItem>()
        }
    }

    iterate<PlayerInput, PlayerInventory, UsingInput> { entity, input, inventory, using ->
        // сомневаюсь насчёт entity.hasComponent<InteractionInterrupt>()
        if (using.input !in input.actions) {
            entity.setComponent(InteractionInterrupt)
            inventory.mainHandInteractor.setComponent(InteractionInterrupt)
            inventory.offHandInteractor.setComponent(InteractionInterrupt)
        }
        if (entity.hasComponent<InteractionInterrupt>()) {
            entity.removeComponent<UsingInput>()
        }
    }
}