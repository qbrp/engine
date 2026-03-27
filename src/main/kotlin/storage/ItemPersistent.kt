package org.lain.engine.storage

import kotlinx.serialization.*
import kotlinx.serialization.cbor.Cbor
import org.lain.engine.container.AssignedSlot
import org.lain.engine.container.ContainedIn
import org.lain.engine.item.*
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.Outfit
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.player.PlayerPart
import org.lain.engine.util.component.*
import org.lain.engine.util.then
import org.lain.engine.world.Location
import org.lain.engine.world.World

@Serializable
data class PersistentItemData(val components: List<ItemData>)

@Serializable
sealed class ItemData {
    @Serializable data class Display(val name: ItemName?, val tooltip: ItemTooltip?, val assets: ItemAssets? = null) : ItemData()
    @Serializable data class Guns(val data: Gun, val display: GunDisplay?) : ItemData()
    @Serializable data class Sounds(val data: ItemSounds) : ItemData()

    @Serializable data class PhysicalParameters(val count: org.lain.engine.item.Count, val mass: org.lain.engine.item.Mass?) : ItemData()
    @Serializable data class Book(@SerialName("writeable") val writableLegacy: Writable? = null, val writable: Writable? = null) : ItemData()
    @Serializable data class Equipment(val hat: Boolean, val outfit: Outfit? = null) : ItemData()

    @Serializable data class Lights(val flashlight: Flashlight) : ItemData()
    @Serializable data class EntityComponents(val components: List<ItemData>) : ItemData()
    @Serializable data class Contained(val containedIn: String, val assignedSLot: AssignedSlot? = null) : ItemData()
    @Serializable data class Container(val persistentId: PersistentId) : ItemData()

    @Serializable @Deprecated("Использовать PhysicalParameters") data class Mass(val value: Float) : ItemData()
    @Serializable @Deprecated("Использовать PhysicalParameters") data class Count(val value: Int) : ItemData()
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

// SAVE

fun itemPersistentData(
    world: World,
    item: ComponentState,
    entityComponents: ComponentState
): PersistentItemData = with(world) {
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

    if (entityComponents.getComponents().isNotEmpty()) {
        val entityComponentsList = mutableListOf<ItemData>()
        val containedIn = item.get<ContainedIn>()
        val assignedSLot = item.get<AssignedSlot>()
        entityComponentsList.addIfNotNull(
            { containedIn != null }.then {
                val containerUuid = containedIn?.container?.getComponent<PersistentId>() ?: error("Container persistent id not found")
                ItemData.Contained(containerUuid.uuid, assignedSLot?.copy())
            }
        )

        components += ItemData.EntityComponents(entityComponentsList)
    }

    return PersistentItemData(components)
}

@OptIn(ExperimentalSerializationApi::class)
val ITEM_CBOR = Cbor { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
fun serializeItemPersistentComponents(item: PersistentItemData): ByteArray {
    return ITEM_CBOR.encodeToByteArray(item.components)
}

@OptIn(ExperimentalSerializationApi::class)
fun deserializeItemPersistentComponents(array: ByteArray): List<ItemData> {
    return ITEM_CBOR.decodeFromByteArray(array)
}

// LOAD

/**
 * @param containers Используемые предметом контейнеры, **требуемые** для корректной работы. После загрузки контейнеров требуется создать компоненты для предмета
 */
data class ItemLoadResult(
    val protoItem: ProtoItem,
    val containers: List<PersistentId>,
)

// Чистая функция
fun loadItem(data: PersistentItemData, location: Location, id: ItemId, uuid: ItemUuid): ItemLoadResult {
    val components = mutableSetOf<Component>()
    val entityComponents = mutableSetOf<Component>()
    val containers = mutableListOf<PersistentId>()

    fun processComponent(component: ItemData) {
        when(component) {
            is ItemData.Display -> {
                components.addIfNotNull(component.name)
                components.addIfNotNull(component.tooltip)
                components.addIfNotNull(component.assets)
            }
            is ItemData.Guns -> {
                components.addIfNotNull(component.data)
                components.addIfNotNull(component.display)
            }
            is ItemData.PhysicalParameters -> {
                components.addIfNotNull(component.count)
                components.addIfNotNull(component.mass)
            }
            is ItemData.Equipment -> {
                components.addIfNotNull(
                    component.outfit
                        ?: if (component.hat) {
                            Outfit(
                                EquipmentSlot.CAP,
                                OutfitDisplay.Separated,
                                listOf(PlayerPart.HEAD)
                            )
                        } else {
                            null
                        }
                )
            }
            is ItemData.Sounds ->
                components.addIfNotNull(component.data)
            is ItemData.Book ->
                components.add(component.writable ?: component.writableLegacy ?: error("Writeable component doesn't exist"))
            is ItemData.Lights ->
                components.add(component.flashlight)
            is ItemData.Count -> {
                components.addIfNotNull(Count(component.value, 16))
            }
            is ItemData.Mass ->
                components.add(Mass(component.value))
            is ItemData.Contained -> {
                containers.addIfNotNull(PersistentId(component.containedIn))
                entityComponents.addIfNotNull(component.assignedSLot)
            }
            is ItemData.Container -> {
                containers.addIfNotNull(component.persistentId)
            }
            is ItemData.EntityComponents -> component.components.forEach { processComponent(it) }
        }
    }

    data.components.forEach { processComponent(it) }

    return ItemLoadResult(
        itemInstance(
            location.world,
            uuid, id,
            ComponentState(components.toList()),
            ComponentState(entityComponents.toList())
        ),
        containers.toList(),
    )
}