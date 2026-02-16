package org.lain.engine.item

import org.lain.engine.server.ITEM_WRITEABLE_SYNCHRONIZER
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
    val name: ItemName? = null,
    val gun: Gun? = null,
    val gunDisplay: GunDisplay? = null,
    val tooltip: ItemTooltip? = null,
    val count: Int? = null, // null значит всегда 1
    val mass: Mass? = null,
    val writeable: Writeable? = null,
    val sounds: ItemSounds? = null,
)

fun itemInstance(uuid: ItemUuid, location: Location, properties: ItemInstantiationSettings): EngineItem {
    val state = ComponentState().apply {
        if (properties.count != null) set(Count(properties.count))
        setNullable(properties.name?.copy())
        setNullable(properties.gun?.copy())
        setNullable(properties.gunDisplay?.copy())
        setNullable(properties.tooltip?.copy())
        setNullable(properties.sounds?.copy())
        setNullable(properties.mass?.copy())
        setNullable(properties.writeable?.copy())

    }
    return itemInstance(uuid, properties.id, location, state)
}

fun itemInstance(uuid: ItemUuid, id: ItemId, location: Location, state: ComponentState): EngineItem {
    return EngineItem(id, uuid, state).apply {
        set(location.copy())
        if (has<Writeable>()) {
            set(Synchronizations<EngineItem>(mutableMapOf()))
                .also { it.initializeSynchronizers() }
        }
    }
}

private fun Synchronizations<EngineItem>.initializeSynchronizers() {
    submit(ITEM_WRITEABLE_SYNCHRONIZER)
}