package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import org.lain.engine.item.*
import org.lain.engine.util.*

@Serializable
data class PersistentItemData(val components: List<ItemData>)

@Serializable
sealed class ItemData {
    @Serializable
    data class Display(val name: ItemName?, val tooltip: ItemTooltip?, val assets: ItemAssets? = null) : ItemData()
    @Serializable
    data class Guns(val data: Gun, val display: GunDisplay?) : ItemData()
    @Serializable
    data class Sounds(val data: ItemSounds) : ItemData()

    @Serializable
    data class PhysicalParameters(val count: org.lain.engine.item.Count, val mass: org.lain.engine.item.Mass?) : ItemData()
    @Serializable
    data class Book(val writable: Writable) : ItemData()

    @Serializable
    data class Equipment(val hat: Boolean) : ItemData()

    @Serializable
    @Deprecated("Использовать PhysicalParameters")
    data class Mass(val value: Float) : ItemData()
    @Serializable
    @Deprecated("Использовать PhysicalParameters")
    data class Count(val value: Int) : ItemData()
}

fun <T : ItemData> MutableList<ItemData>.addIf(statement: () -> Boolean, component: () -> T) {
    if (statement()) this += component()
}

fun <T : Any> MutableCollection<T>.addIfNotNull(component: T?) {
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
    components.add(
        ItemData.PhysicalParameters(
            item.require<Count>().copy(),
            item.get<Mass>()
        )
    )
    components.addIfNotNull(item.wrap<Writable> { ItemData.Book(it.copy())  })
    components.addIfNotNull(
        ItemData.Equipment(
            item.has<Hat>()
        ).takeIf { it.hat }
    )

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
