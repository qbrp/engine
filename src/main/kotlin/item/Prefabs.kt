package org.lain.engine.item

import org.lain.engine.util.ComponentState
import org.lain.engine.util.set
import java.util.UUID

data class ItemPrefab(
    val id: ItemId,
    val name: ItemName
)

fun bakeItem(prefab: ItemPrefab): EngineItem {
    return EngineItem(
        prefab.id,
        ItemUuid(UUID.randomUUID()),
        ComponentState()
    ).apply {
        set(prefab.name)
    }
}

class ItemPrefabStorage() {
    private var prefabs = mapOf<ItemId, ItemPrefab>()

    fun load(prefabs: List<ItemPrefab>) {
        this.prefabs = prefabs.associateBy { it.id }
    }

    fun get(id: ItemId) = prefabs[id] ?: error("Префаб $id не существует")
}