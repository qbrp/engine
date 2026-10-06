package org.lain.engine.server.replication

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.equipment
import org.lain.engine.player.mainContainer
import org.lain.engine.player.require
import org.lain.engine.world.World

context(world: World)
fun EnginePlayer.collectReplicationEntities(): Set<EntityId> {
    val inventory = require<PlayerInventory>()
    val equipment = equipment

    return buildSet {
        add(entity)
        add(mainContainer)

        addAll(inventory.items)
        inventory.cursorItem?.let(::add)
        inventory.mainHandItem?.let(::add)
        inventory.offHandItem?.let(::add)
        add(inventory.mainHandInteractor)
        add(inventory.offHandInteractor)

        addAll(equipment.values)
    }
}