package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.BuiltinNamespaces
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.Uuid
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

fun WriteComponentAccess.createItem(
    prefab: ItemPrefab,
    uuid: PersistentId = Uuid.next()
): EngineItem {
    val item = addEntity()
    item.setComponent(PersistentIdComponent(uuid))
    item.setComponent(Item(uuid, prefab.id))
    item.setComponent(ItemName(prefab.name))
    item.setComponent(Networked)
    item.setComponent(Count(1, prefab.maxCount))
    item.createDebugName(prefab.id)
    prefab.progressionAnimations?.let { item.setComponent(it) }
    prefab.assets?.let { item.setComponent(it) }
    prefab.onLoad(item)
    return item
}

context(world: WriteComponentAccess)
fun EngineItem.createDebugName(id: ItemId) {
    setComponent(DebugName(id.toString()))
}