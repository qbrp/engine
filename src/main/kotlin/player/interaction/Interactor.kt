package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.item.EngineItem
import org.lain.engine.player.PlayerInventory
import org.lain.engine.server.replication.Networked
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.Pos
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.pos

data class Interactor(val owner: EntityId) : Component

enum class HandType {
    MAIN, OFFHAND
}

data class Hand(
    val owner: EntityId,
    val side: HandType,
    val opposite: EntityId,
    var item: EngineItem? = null
) : Component

context(write: WriteComponentAccess)
fun EntityId.setupHandInteractorEntity(
    owner: EntityId,
    side: HandType,
    opposite: EntityId,
    persistentId: PersistentId,
    pos: Pos
): EntityId {
    setComponent(Interactor(owner))
    setComponent(Location(pos))
    setComponent(Hand(owner, side, opposite, null))
    setComponent(PersistentIdComponent(persistentId))
    setComponent(Networked)
    return this
}

fun World.tickInteractorLocations() = iterate<Interactor, Location>() { _, (owner), location ->
    location.position.set(owner.requireComponent<Location>().position)
}

fun World.tickPlayerHandSystem() = iterate<PlayerInventory>() { _, inventory ->
    inventory.mainHandInteractor.requireComponent<Hand>().item = inventory.mainHandItem
    inventory.offHandInteractor.requireComponent<Hand>().item = inventory.offHandItem
}
