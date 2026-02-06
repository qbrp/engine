package org.lain.engine.item

import org.lain.engine.util.ComponentState
import org.lain.engine.util.set
import org.lain.engine.util.setNullable
import org.lain.engine.world.Location

data class ItemPrefab(val properties: ItemInstantiationSettings) {
    val id get() = properties.id
}

fun bakeItem(location: Location, prefab: ItemPrefab): EngineItem {
    return itemInstance(ItemUuid.next(), location, prefab.properties.copy())
}

data class ItemInstantiationSettings(
    val id: ItemId,
    val name: ItemName? = null,
    val gun: Gun? = null,
    val gunDisplay: GunDisplay? = null,
    val tooltip: ItemTooltip? = null,
    val count: Int? = null, // null значит всегда 1
    val sounds: ItemSounds? = null,
)

fun itemInstance(uuid: ItemUuid, location: Location, properties: ItemInstantiationSettings): EngineItem {
    return EngineItem(properties.id, uuid, ComponentState()).apply {
        if (properties.count != null) set(Count(properties.count))
        set(location.copy())
        setNullable(properties.name?.copy())
        setNullable(properties.gun?.copy())
        setNullable(properties.gunDisplay?.copy())
        setNullable(properties.tooltip?.copy())
        setNullable(properties.sounds?.copy())
    }
}