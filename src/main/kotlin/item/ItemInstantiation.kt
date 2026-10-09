package org.lain.engine.item

import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.EntityCoordinator
import org.lain.engine.script.BuiltinNamespaces
import org.lain.engine.server.EngineServer
import org.lain.engine.data.PersistentId
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.data.Uuid
import org.lain.engine.util.DebugName
import org.lain.engine.server.replication.Networked
import org.lain.engine.world.World

data class ItemPrefab(
    val id: ItemId,
    val maxCount: Int,
    val name: String,
    val assets: ItemAssets?,
    val progressionAnimations: ItemProgressionAnimations?,
    val create: (World, EngineItem) -> Unit,
)

context(write: WriteComponentAccess)
fun EngineServer.createInvalidItem(world: World): EngineItem {
    val prefab = namespacedStorage.items[BuiltinNamespaces.Items.INVALID_ID]
        ?: BuiltinNamespaces.Items.INVALID
    return createItemCommon(world, prefab, entityCoordinator)
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
    setComponent(ItemSounds(emptyMap()))
}

fun World.createItem(
    prefab: ItemPrefab,
    coordinator: EntityCoordinator?,
    uuid: PersistentId = Uuid.next(),
): EngineItem {
    val item = createItemCommon(this, prefab, coordinator, uuid)
    prefab.create(this, item)
    return item
}


context(write: WriteComponentAccess)
fun createItemCommon(
    world: World,
    prefab: ItemPrefab,
    coordinator: EntityCoordinator?,
    uuid: PersistentId = Uuid.next(),
): EngineItem {
    val item = world.addEntity()
    item.setComponent(PersistentIdComponent(uuid))
    item.setComponent(Item(uuid, prefab.id))
    item.setComponent(ItemName(prefab.name))
    item.setRequiredItemComponents(1, prefab.maxCount, prefab.id)
    prefab.progressionAnimations?.let { item.setComponent(it) }
    prefab.assets?.let { item.setComponent(it) }
    coordinator?.registerEntity(world, uuid, item)
    return item
}