package org.lain.engine.item

import org.lain.cyberia.ecs.*
import org.lain.engine.script.INVALID_ITEM_ID
import org.lain.engine.server.EngineServer
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
    val tooltipFactory: () -> ItemTooltip?,
    val componentsFactory: () -> List<Component> = { emptyList() },
)

// Неинициализированный предмет
// Создаётся на сервере. Клиент лишь получает список компонентов через EntityDeltaPacket
data class ProtoItem(
    val prefabId: ItemId,
    val uuid: PersistentId,
    val world: World,
    val state: ComponentState
)

fun EngineServer.bakeInvalidProtoItem(world: World): ProtoItem {
    return bakeProtoItem(world, namespacedStorage.items[ItemId(INVALID_ITEM_ID)]!!)
}

fun bakeProtoItem(world: World, prefab: ItemPrefab): ProtoItem {
    return protoItemInstance(world, PersistentId.next(), prefab)
}

fun protoItemInstance(world: World, uuid: PersistentId, prefab: ItemPrefab): ProtoItem {
    val state = ComponentState(prefab.componentsFactory())
    state.set(ItemName(prefab.name))
    state.setNullable(prefab.progressionAnimations)
    state.setNullable(prefab.assets)
    state.setNullable(prefab.tooltipFactory.invoke())
    return protoItemInstance(world, uuid, prefab.id, state)
}

fun protoItemInstance(
    world: World,
    uuid: PersistentId,
    id: ItemId,
    state: ComponentState
): ProtoItem {
    val item = ProtoItem(id, uuid, world, state)
    return item
}

// INSTANTIATION

fun WriteComponentAccess.instantiateItem(
    world: World,
    prefab: ItemPrefab,
    itemStorage: Storage<String, EngineItem>,
): EngineItem {
    return instantiateItem(bakeProtoItem(world, prefab), itemStorage)
}

fun WriteComponentAccess.instantiateItem(item: ProtoItem, itemStorage: Storage<String, EngineItem>): EngineItem {
    val engineItem = addEntity()
    engineItem.setComponent(PersistentId(item.uuid.toString()))
    engineItem.setComponent(ItemMeta(item.uuid, item.prefabId))
    engineItem.addItemComponents()
    engineItem.copyState(item.state)
    itemStorage.add(item.uuid.value, engineItem)
    return engineItem
}

/**
 * Использовать перед добавлением компонентов
 */
context(world: WriteComponentAccess)
fun EngineItem.addItemComponents() {
    setComponent(Item)
    setComponent(Networked)
    setComponent(UpdateMeta(false))
    setComponent(Count(1, 1))
}