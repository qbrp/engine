package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.require
import org.lain.engine.container.AssignedSlot
import org.lain.engine.item.*
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.Outfit
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.player.PlayerPart
import org.lain.engine.util.addIfNotNull
import org.lain.engine.util.component.ComponentState
import org.lain.engine.world.World

/////////////// LEGACY SAVING

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

@OptIn(ExperimentalSerializationApi::class)
val ITEM_CBOR = Cbor { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
fun deserializeItemPersistentComponents(array: ByteArray): List<ItemData> {
    return ITEM_CBOR.decodeFromByteArray(array)
}

////////////////// LOAD

data class EntryVersion(val value: Int) : Component {
    companion object {
        val DEFAULT = EntryVersion(0)
    }
}

/**
 * @see ItemLoader.loadWorldItem
 */
fun loadItem(
    world: World,
    persistentId: PersistentId,
    components: List<ComponentDto>,
    componentLoadSettings: ComponentLoadSettings,
): ItemLoadResult {
    val components2 = mutableListOf<Component>()
    var container: PersistentId? = null
    components.forEach {
        if (it.data is ContainedInDto) {
            container = it.data.container
        } else { // контейнер подгрузится позже
            components2.add(it.toDomain(componentLoadSettings) ?: run {
                LOGGER.warn("При загрузке сущности $persistentId был пропущен компонент $it")
                return@forEach
            })
        }
    }
    val componentState = ComponentState(components2)
    // использовать в будущем для версионирования
    // val entry = componentState.get<EntryVersion>() ?: EntryVersion.DEFAULT
    return ItemLoadResult(
        ProtoItem(
        componentState.require<ItemMeta>().id,
        persistentId,
            world,
            componentState,
        ),
        container
    )
}

/**
 * @param container Используемый предметом контейнеры для корректной работы. После загрузки контейнеров требуется создать компоненты для предмета
 */
data class ItemLoadResult(
    val protoItem: ProtoItem,
    val container: PersistentId? = null,
)

/**
 * Чистая функция.
 * @deprecated C 22.04.2026 предметы загружаются как обычные сущности и не нуждаются в отдельном пайплайне
 */
fun loadItemLegacy(data: PersistentItemData, world: World, id: ItemId, uuid: PersistentId): ItemLoadResult {
    val components = mutableSetOf<Component>()
    val entityComponents = mutableSetOf<Component>()
    var container: PersistentId? = null

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
                container = PersistentId(component.containedIn)
                entityComponents.addIfNotNull(component.assignedSLot)
            }
            is ItemData.Container -> {
                container = component.persistentId
            }
            is ItemData.EntityComponents -> component.components.forEach { processComponent(it) }
        }
    }

    data.components.forEach { processComponent(it) }

    return ItemLoadResult(
        protoItemInstance(
            world,
            uuid, id,
            ComponentState(components.toList() + entityComponents.toList()),
        ),
        container
    )
}