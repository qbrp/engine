package org.lain.engine.item

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.copyState
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.INVALID_ITEM_ID
import org.lain.engine.server.EngineServer
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.Uuid
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

fun EngineServer.createInvalidItem(world: World): EngineItem {
    val prefab = namespacedStorage.items[ItemId(INVALID_ITEM_ID)]!!
    return world.createItem(prefab)
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
    item.setComponent(UpdateMeta(false))
    item.setComponent(Count(1, prefab.maxCount))
    prefab.progressionAnimations?.let { item.setComponent(it) }
    prefab.assets?.let { item.setComponent(it) }
    prefab.tooltipFactory.invoke()?.let { item.setComponent(it) }
    val components = prefab.componentsFactory()
    item.copyState(components)
    return item
}