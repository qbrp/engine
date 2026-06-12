 package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.engine.container.AssignedSlot
import org.lain.engine.item.*
import org.lain.engine.player.EquipmentSlot
import org.lain.engine.player.Outfit
import org.lain.engine.player.OutfitDisplay
import org.lain.engine.player.PlayerPart
import org.lain.engine.util.addIfNotNull

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
/**
 * @deprecated C 22.04.2026 предметы загружаются как обычные сущности и не нуждаются в отдельном пайплайне
 */
fun WriteComponentAccess.loadItemLegacy(data: PersistentItemData, id: ItemId, uuid: PersistentId): EngineItem {
    val components = mutableSetOf<Component>()
    val entityComponents = mutableSetOf<Component>()
    var count: Count? = null
    var assets: ItemAssets? = null
    var tooltip: ItemTooltip? = null
    var name: ItemName? = null

    fun processComponent(component: ItemData) {
        when(component) {
            is ItemData.Display -> {
                name = component.name
                tooltip = component.tooltip
                assets = component.assets
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
                count = Count(component.value, 16)
            }
            is ItemData.Mass ->
                components.add(Mass(component.value))
            // Контейнеры в легаси-системе не поддерживаются
            is ItemData.Contained -> {}
            is ItemData.Container -> {}
            is ItemData.EntityComponents -> component.components.forEach { processComponent(it) }
        }
    }
    data.components.forEach { processComponent(it) }

    return createItem(
        ItemPrefab(
            id,
            count?.max ?: 1,
            name?.text ?: "Предмет",
            assets ?: ItemAssets(mutableMapOf()),
            ItemProgressionAnimations(mutableMapOf()),
            { tooltip },
            { components.toList() + entityComponents.toList() }
        ),
        uuid
    )
}