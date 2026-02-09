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
    data class Display(val name: ItemName?, val tooltip: ItemTooltip?) : ItemData()
    data class Guns(val data: Gun, val display: GunDisplay?) : ItemData()
    data class Sounds(val data: ItemSounds) : ItemData()
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
            name = item.get<ItemName>(),
            tooltip = item.get<ItemTooltip>()
        )
            .takeIf { it.name != null || it.tooltip != null })

    components.addIfNotNull(item.wrap<ItemSounds> { ItemData.Sounds(it) })
    components.addIfNotNull(item.wrap<Gun> { ItemData.Guns(it, item.get()) })

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
