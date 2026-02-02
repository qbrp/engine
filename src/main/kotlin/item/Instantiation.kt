package org.lain.engine.item

import org.lain.engine.mc.ItemProperties
import org.lain.engine.util.ComponentState
import org.lain.engine.util.set
import org.lain.engine.util.setNullable
import org.lain.engine.world.Location

data class ItemPrefab(val properties: ItemInstantiationProperties) {
    val id get() = properties.id
}

fun bakeItem(location: Location, prefab: ItemPrefab): EngineItem {
    return itemInstance(ItemUuid.next(), location, prefab.properties.copy())
}

data class ItemInstantiationProperties(
    val id: ItemId,
    val name: ItemName? = null,
    val gun: Gun? = null,
    val gunDisplay: GunDisplay? = null,
    val tooltip: ItemTooltip? = null,
    val sounds: ItemSounds? = null
)

fun itemInstance(uuid: ItemUuid, location: Location, properties: ItemInstantiationProperties): EngineItem {
    return EngineItem(properties.id, uuid, ComponentState()).apply {
        set(location.copy())
        setNullable(properties.name?.copy())
        setNullable(properties.gun?.copy())
        setNullable(properties.gunDisplay?.copy())
        setNullable(properties.tooltip?.copy())
        setNullable(properties.sounds?.copy())
    }
}