package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.lain.engine.item.*
import org.lain.engine.util.Component
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.get

@Serializable
data class PersistentItemData(val components: List<ItemData>)

@Serializable
sealed class ItemData {
    @Serializable
    data class Display(val name: ItemName?, val tooltip: ItemTooltip?) : ItemData()
    @Serializable
    data class Guns(val data: Gun, val display: GunDisplay?) : ItemData()
    @Serializable
    data class Sounds(val data: ItemSounds) : ItemData()

    @Serializable
    data class Count(val value: Int) : ItemData()
    @Serializable
    data class Mass(val value: Float) : ItemData()
    @Serializable
    data class Book(val writeable: Writeable) : ItemData()
}

fun <T : ItemData> MutableList<ItemData>.addIf(statement: () -> Boolean, component: () -> T) {
    if (statement()) this += component()
}

fun <T : Any> MutableList<T>.addIfNotNull(component: T?) {
    if (component != null) this += component
}

inline fun <reified T : Component> ComponentManager.wrap(statement: (T) -> ItemData): ItemData? {
    return get<T>()?.let { statement(it) }
}

fun itemPersistentData(item: EngineItem): PersistentItemData {
    val components = mutableListOf<ItemData>()

    components.addIfNotNull(
        ItemData.Display(
            name = item.get<ItemName>()?.copy(),
            tooltip = item.get<ItemTooltip>()?.copy()
        )
            .takeIf { it.name != null || it.tooltip != null })

    components.addIfNotNull(item.wrap<ItemSounds> { ItemData.Sounds(it.copy()) })
    components.addIfNotNull(item.wrap<Gun> { ItemData.Guns(it.copy(), item.get<GunDisplay>()?.copy()) })
    components.addIfNotNull(item.wrap<Count> { ItemData.Count(it.value)  })
    components.addIfNotNull(item.wrap<Mass> { ItemData.Mass(it.mass)  })
    components.addIfNotNull(item.wrap<Writeable> { ItemData.Book(it.copy())  })

    return PersistentItemData(components)
}

@OptIn(ExperimentalSerializationApi::class)
fun serializeItemPersistentComponents(item: PersistentItemData): ByteArray {
    return Cbor.encodeToByteArray(item.components)
}

@OptIn(ExperimentalSerializationApi::class)
fun deserializeItemPersistentComponents(array: ByteArray): List<ItemData> {
    return Cbor.decodeFromByteArray(array)
}
