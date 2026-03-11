package org.lain.engine.storage

import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import org.lain.engine.item.*
import org.lain.engine.player.Outfit
import org.lain.engine.util.component.*
import org.lain.engine.util.then

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
    data class Book(
        @SerialName("writeable") val writableLegacy: Writable? = null,
        val writable: Writable? = null
    ) : ItemData()

    @Serializable
    data class Equipment(val hat: Boolean, val outfit: Outfit? = null) : ItemData()

    @Serializable
    data class Lights(val flashlight: Flashlight) : ItemData()

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

    val name = item.get<ItemName>()
    val tooltip = item.get<ItemTooltip>()
    components.addIfNotNull(
        { name != null || tooltip != null }.then { ItemData.Display(name?.copy(), tooltip?.copy()) }
    )

    components.addIfNotNull(item.wrap<ItemSounds> { ItemData.Sounds(it.copy()) })
    components.addIfNotNull(item.wrap<Gun> { ItemData.Guns(it.copy(), item.get<GunDisplay>()?.copy()) })
    components.add(
        ItemData.PhysicalParameters(
            item.require<Count>().copy(),
            item.get<Mass>()
        )
    )
    components.addIfNotNull(item.wrap<Writable> { ItemData.Book(writable=it.copy())  })
    val outfit = item.get<Outfit>()
    components.addIfNotNull(
        { outfit != null }.then { ItemData.Equipment(false, outfit) }
    )

    item.handle<Flashlight> {
        components.add(ItemData.Lights(this.copy()))
    }

    return PersistentItemData(components)
}

@OptIn(ExperimentalSerializationApi::class)
val ITEM_CBOR = Cbor {
    ignoreUnknownKeys = true
}

@OptIn(ExperimentalSerializationApi::class)
fun serializeItemPersistentComponents(item: PersistentItemData): ByteArray {
    return ITEM_CBOR.encodeToByteArray(item.components)
}

@OptIn(ExperimentalSerializationApi::class)
fun deserializeItemPersistentComponents(array: ByteArray): List<ItemData> {
    return ITEM_CBOR.decodeFromByteArray(array)
}
