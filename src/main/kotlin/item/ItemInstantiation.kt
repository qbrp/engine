package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.Networked
import org.lain.engine.world.World

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
    val uuid: PersistentId,
    val world: World,
    val state: ComponentState
)

fun bakeItem(world: World, prefab: ItemPrefab): ProtoItem {
    return itemInstance(world, PersistentId.next(), prefab)
}

fun itemInstance(world: World, uuid: PersistentId, prefab: ItemPrefab): ProtoItem {
    val state = ComponentState(prefab.componentsFactory())
    return itemInstance(world, uuid, prefab.id, state)
}

fun itemInstance(
    world: World,
    uuid: PersistentId,
    id: ItemId,
    state: ComponentState
): ProtoItem {
    val item = ProtoItem(id, uuid, world, state)
    return item
}

fun WriteComponentAccess.instantiateItem(
    world: World,
    prefab: ItemPrefab,
    itemStorage: Storage<String, EngineItem>,
): EngineItem {
    return instantiateItem(bakeItem(world, prefab), itemStorage)
}

fun WriteComponentAccess.instantiateItem(item: ProtoItem, itemStorage: Storage<String, EngineItem>): EngineItem {
    val engineItem = addEntity()
    engineItem.setComponent(Item)
    engineItem.setComponent(Networked)
    engineItem.setComponent(PersistentId(item.uuid.toString()))
    engineItem.setComponent(ItemMeta(item.uuid, item.prefabId))
    engineItem.setComponent(UpdateMeta(false))
    engineItem.copyState(item.state)
    itemStorage.add(item.uuid.value, engineItem)
    return engineItem
}