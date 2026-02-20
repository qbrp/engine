package org.lain.engine.item

import org.lain.engine.server.ITEM_WRITABLE_SYNCHRONIZER
import org.lain.engine.server.Synchronizations
import org.lain.engine.server.submit
import org.lain.engine.util.ComponentState
import org.lain.engine.util.has
import org.lain.engine.util.set
import org.lain.engine.util.setNullable
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
    val hat: Boolean = false,
    val assets: ItemAssets? = null,
    val sounds: ItemSounds? = null,
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
        if (properties.hat) set(Hat)
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
        if (has<Writable>()) {
            set(Synchronizations<EngineItem>(mutableMapOf()))
                .also { it.initializeSynchronizers() }
        }
    }
}

private fun Synchronizations<EngineItem>.initializeSynchronizers() {
    submit(ITEM_WRITABLE_SYNCHRONIZER)
}