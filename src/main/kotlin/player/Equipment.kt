package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.ReadComponentAccess
import org.lain.engine.container.ContainerEntity
import org.lain.engine.container.SlotId
import org.lain.engine.container.collectEntriesRecursive
import org.lain.engine.container.getContainerItems
import org.lain.engine.item.EngineItem
import org.lain.engine.player.interaction.VerbType
import org.lain.engine.world.World

@Serializable
enum class EquipmentSlot(name: String, val slotId: SlotId = SlotId(name)) {
    CAP("cap"),
    WEST("west"),
    MASK("mask");

    companion object {
        val slotIds = entries.map { it.slotId }.toSet()

        fun ofSlot(slotId: SlotId): EquipmentSlot {
            return entries.first { it.slotId == slotId }
        }
    }
}


data class Equipment(val container: ContainerEntity) : Component

// Вешать на сущность контейнера
data class PlayerEquipment(val player: EnginePlayer) : Component

val EnginePlayer.equipmentContainer: ContainerEntity
    get() = this.require<Equipment>().container

context(access: ReadComponentAccess)
fun EnginePlayer.collectOwnedItems(): List<EngineItem> {
    return equipmentContainer.collectEntriesRecursive()
}

context(world: World)
fun EnginePlayer.collectReplicationEntities(): Set<EntityId> {
    val inventory = require<PlayerInventory>()
    val equipment = equipmentContainer

    return buildSet {
        add(entity)
        add(mainContainer)
        equipment?.entity?.let { add(it) }

        addAll(inventory.items)
        inventory.cursorItem?.let(::add)
        inventory.mainHandItem?.let(::add)
        inventory.offHandItem?.let(::add)

        equipment?.getContainerItems()?.let { addAll(it) }
    }
}