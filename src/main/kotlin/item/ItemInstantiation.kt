package org.lain.engine.item

import org.lain.engine.container.Item
import org.lain.engine.server.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.Networked
import org.lain.engine.util.component.WriteComponentAccess
import org.lain.engine.util.component.copyState
import org.lain.engine.util.component.set
import org.lain.engine.util.component.setComponent
import org.lain.engine.world.World
import org.lain.engine.world.location
import kotlin.apply
import kotlin.collections.any
import kotlin.let

data class ItemPrefab(
    val id: ItemId,
    val maxCount: Int,
    val name: String,
    val assets: ItemAssets?,
    val progressionAnimations: ItemProgressionAnimations?,
    val componentsFactory: () -> List<Component>,
)

// Неинициализированный предмет
data class ProtoItem(
    val prefabId: ItemId,
    val uuid: ItemUuid,
    val world: World,
    val state: ComponentState,
    val entityState: ComponentState?,
)

fun bakeItem(world: World, prefab: ItemPrefab): ProtoItem {
    return itemInstance(world, ItemUuid.next(), prefab)
}

fun itemInstance(world: World, uuid: ItemUuid, prefab: ItemPrefab): ProtoItem {
    val state = ComponentState(prefab.componentsFactory())
    return itemInstance(world, uuid, prefab.id, state, null)
}

fun itemInstance(
    world: World,
    uuid: ItemUuid,
    id: ItemId,
    state: ComponentState,
    entityState: ComponentState?,
): ProtoItem {
    val item = ProtoItem(id, uuid, world, state, entityState)
    item.state.apply {
        set(UpdateMeta(false))
        if (any { it is ItemSynchronizable }) {
            set(Synchronizations<EngineItem>(mutableMapOf()))
                .also { it.initializeSynchronizers() }
        }
    }
    return item
}

private fun Synchronizations<EngineItem>.initializeSynchronizers() {
    submit(ITEM_WRITABLE_SYNCHRONIZER)
    submit(ITEM_GUN_SYNCHRONIZER)
    submit(ITEM_FLASHLIGHT_SYNCHRONIZER)
}

fun WriteComponentAccess.instantiateItem(
    world: World,
    prefab: ItemPrefab,
    itemStorage: Storage<ItemUuid, EngineItem>,
): EngineItem {
    return instantiateItem(bakeItem(world, prefab), itemStorage)
}

fun WriteComponentAccess.instantiateItem(item: ProtoItem, itemStorage: Storage<ItemUuid, EngineItem>): EngineItem {
    val itemEntity = addEntity()
    val engineItem = EngineItem(item.prefabId, item.uuid, itemEntity, item.state)
    itemEntity.setComponent(Item(engineItem))
    itemEntity.setComponent(Networked)
    itemEntity.setComponent(PersistentId(item.uuid.toString()))
    item.entityState?.let { itemEntity.copyState(it) }
    itemStorage.add(item.uuid, engineItem)
    return engineItem
}

fun destroyItem(item: EngineItem, storage: Storage<ItemUuid, EngineItem>) {
    item.location.world.destroy(item.entity)
    storage.remove(item.uuid)
}