package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.BuiltinNamespaces
import org.lain.engine.server.EngineServer
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.RawEngineId
import org.lain.engine.data.Uuid
import org.lain.engine.util.DebugName
import org.lain.engine.server.Networked
import org.lain.engine.world.World

data class ItemPrefab(
    val id: ItemId,
    val maxCount: Int,
    val name: String,
    val assets: ItemAssets?,
    val progressionAnimations: ItemProgressionAnimations?,
    val onLoad: context(WriteComponentAccess) (EngineItem) -> Unit,
)

fun EngineServer.createInvalidItem(world: World): EngineItem = with(world) {
    createInvalidItem()
}

context(write: WriteComponentAccess)
fun EngineServer.createInvalidItem(): EngineItem {
    val prefab = namespacedStorage.items[BuiltinNamespaces.Items.INVALID_ID]!!
    return write.createItem(prefab)
}

context(write: WriteComponentAccess)
fun EntityId.setRequiredItemComponents(
    count: Int,
    maxCount: Int,
    prefabId: ItemId,
) {
    setComponent(Count(count, maxCount))
    setComponent(Networked)
    setComponent(DebugName(prefabId.toString()))
}

fun WriteComponentAccess.createItem(
    prefab: ItemPrefab,
    uuid: PersistentId = Uuid.next()
): EngineItem {
    val item = addEntity()
    item.setComponent(PersistentIdComponent(uuid))
    item.setComponent(Item(uuid, prefab.id))
    item.setComponent(ItemName(prefab.name))
    item.setRequiredItemComponents(1, prefab.maxCount, prefab.id)
    prefab.progressionAnimations?.let { item.setComponent(it) }
    prefab.assets?.let { item.setComponent(it) }
    prefab.onLoad(item)
    return item
}