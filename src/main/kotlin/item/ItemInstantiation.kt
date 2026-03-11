package org.lain.engine.item

import org.lain.engine.server.*
import org.lain.engine.util.Storage
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.set
import org.lain.engine.world.Location

data class ItemPrefab(
    val id: ItemId,
    val maxCount: Int,
    val name: String,
    val assets: ItemAssets?,
    val progressionAnimations: ItemProgressionAnimations?,
    val componentsFactory: () -> List<Component>,
)

fun bakeItem(location: Location, prefab: ItemPrefab): EngineItem {
    return itemInstance(ItemUuid.next(), location, prefab)
}

fun itemInstance(uuid: ItemUuid, location: Location, prefab: ItemPrefab): EngineItem {
    val state = ComponentState(prefab.componentsFactory())
    return itemInstance(uuid, prefab.id, location, Count(1, prefab.maxCount), state)
}

fun itemInstance(
    uuid: ItemUuid,
    id: ItemId,
    location: Location,
    count: Count,
    state: ComponentState
): EngineItem {
    return EngineItem(id, uuid, state).apply {
        set(UpdateMeta(false))
        set(location.copy())
        set(count.copy())
        if (any { it is ItemSynchronizable }) {
            set(Synchronizations<EngineItem>(mutableMapOf()))
                .also { it.initializeSynchronizers() }
        }
    }
}

private fun Synchronizations<EngineItem>.initializeSynchronizers() {
    submit(ITEM_WRITABLE_SYNCHRONIZER)
    submit(ITEM_GUN_SYNCHRONIZER)
    submit(ITEM_FLASHLIGHT_SYNCHRONIZER)
}

fun createItem(
    location: Location,
    prefab: ItemPrefab,
    itemStorage: Storage<ItemUuid, EngineItem>
): EngineItem {
    val item = bakeItem(location, prefab)
    itemStorage.add(item.uuid, item)
    return item
}