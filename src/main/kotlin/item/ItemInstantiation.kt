package org.lain.engine.item

import org.lain.engine.player.Outfit
import org.lain.engine.server.*
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.set
import org.lain.engine.util.component.setNullable
import org.lain.engine.world.Location

data class ItemPrefab(val properties: ItemInstantiationSettings) {
    val id get() = properties.id
}

fun bakeItem(location: Location, prefab: ItemPrefab): EngineItem {
    return itemInstance(ItemUuid.next(), location, prefab.properties)
}

data class ItemInstantiationSettings(
    val id: ItemId,
    val maxCount: Int,
    val name: ItemName? = null,
    val gun: Gun? = null,
    val gunDisplay: GunDisplay? = null,
    val tooltip: ItemTooltip? = null,
    val mass: Mass? = null,
    val writable: Writable? = null,
    val assets: ItemAssets? = null,
    val outfit: Outfit? = null,
    val flashlight: Flashlight? = null,
    val sounds: ItemSounds? = null,
    val progressionAnimations: ItemProgressionAnimations? = null,
)

fun itemInstance(uuid: ItemUuid, location: Location, properties: ItemInstantiationSettings): EngineItem {
    val state = ComponentState().apply {
        setNullable(properties.name?.copy())
        setNullable(properties.gun?.copy())
        setNullable(properties.gunDisplay?.copy())
        setNullable(properties.tooltip?.copy())
        setNullable(properties.sounds?.copy())
        setNullable(properties.mass?.copy())
        setNullable(properties.writable?.copy())
        setNullable(properties.assets?.copy())
        setNullable(properties.flashlight?.copy())
        setNullable(properties.progressionAnimations?.copy())
        setNullable(properties.outfit?.copy())
    }
    return itemInstance(uuid, properties.id, location, Count(1, properties.maxCount), state)
}

fun itemInstance(
    uuid: ItemUuid,
    id: ItemId,
    location: Location,
    count: Count,
    state: ComponentState
): EngineItem {
    return EngineItem(id, uuid, state).apply {
        set(location.copy())
        set(count.copy())
        if (any { it is ItemSynchronizable }) {
            set(Synchronizations<EngineItem>(mutableMapOf()))
                .also { it.initializeSynchronizers() }
        }
    }
}

private fun Synchronizations<EngineItem>.initializeSynchronizers() {
    submit(ITEM_WRITABLE_SYNCHRONIZER)
    submit(ITEM_GUN_SYNCHRONIZER)
    submit(ITEM_FLASHLIGHT_SYNCHRONIZER)
}

fun createItem(
    location: Location,
    prefab: ItemPrefab,
    itemStorage: Storage<ItemUuid, EngineItem>
): EngineItem {
    val item = bakeItem(location, prefab)
    itemStorage.add(item.uuid, item)
    return item
}