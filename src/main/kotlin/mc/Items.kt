package org.lain.engine.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlinx.serialization.Serializable
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.EquippableComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.name
import org.lain.engine.util.EngineId
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.injectItemAccess
import org.lain.engine.util.text.parseMiniMessage
import java.util.*
import kotlin.jvm.optionals.getOrNull

data class ItemListTab(
    val id: String,
    val name: String,
    val items: List<ItemId>
)

data class EngineItemContext(
    val itemPropertiesStorage: NamespacedStorage<ItemId, ItemProperties> = NamespacedStorage(),
    var tabs: List<ItemListTab> = mutableListOf(),
)

data class ItemProperties(
    val id: ItemId,
    val material: Identifier,
    val asset: Identifier,
    val maxStackSize: Int,
    val equipment: ItemEquipment? = null
) {
    fun getMaterialStack(): ItemStack {
        return Registries.ITEM.get(material).defaultStack ?: error("Illegal material id")
    }
}

@Serializable
data class ItemEquipment(val slot: EquipmentSlot)

fun detachEngineItemStack(itemStack: ItemStack) {
    itemStack.remove(ENGINE_ITEM_REFERENCE_COMPONENT)
    itemStack.set(
        DataComponentTypes.LORE,
        LoreComponent(
            listOf(
                "<reset><red>Предмет отключен из-за ошибки Engine".parseMiniMessage(),
                "<reset><red>Он не будет отвечать на действия игрока и обновлять состояние".parseMiniMessage()
            )
        )
    )
}

fun updateEngineItemStack(itemStack: ItemStack, item: EngineItem) {
    wrapEngineItemStackVisual(itemStack, item.name)
}

fun wrapEngineItemStackVisual(
    itemStack: ItemStack,
    name: String,
    asset: Identifier? = null
) {
    val currentName = itemStack.get(DataComponentTypes.ITEM_NAME)
    if (currentName?.string != name) {
        itemStack.set(
            DataComponentTypes.ITEM_NAME,
            Text.of(name)
        )
    }
    if (asset != null) {
        itemStack.set(
            DataComponentTypes.ITEM_MODEL,
            asset
        )
    }
}

fun wrapEngineItemStackBase(itemStack: ItemStack, maxStackSize: Int, equipment: ItemEquipment?) {
    itemStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    itemStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        maxStackSize
    )
    equipment?.let {
        itemStack.set(
            DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(it.slot)
                .allowedEntities(EntityType.PLAYER)
                .build()
        )
    }
}

fun wrapEngineItemStack(
    properties: ItemProperties,
    item: EngineItem,
    itemStack: ItemStack
): ItemStack {
    wrapEngineItemStackVisual(itemStack, item.name, properties.asset)
    wrapEngineItemStackBase(itemStack, properties.maxStackSize, properties.equipment)

    itemStack.set(
        ENGINE_ITEM_REFERENCE_COMPONENT,
        EngineItemReferenceComponent(item.id, item.uuid, CURRENT_ITEM_VERSION)
    )
    return itemStack
}

fun ItemStack.engine() = get(ENGINE_ITEM_REFERENCE_COMPONENT)

fun ItemStack.engineItem() = get(ENGINE_ITEM_REFERENCE_COMPONENT)?.getItem()

val ENGINE_ITEM_INSTANTIATE_COMPONENT: ComponentType<String> = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    EngineId("initialize-component"),
    ComponentType
        .builder<String>()
        .codec(Codec.STRING)
        .build()
)

val ENGINE_ITEM_REFERENCE_COMPONENT: ComponentType<EngineItemReferenceComponent> = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    EngineId("reference-component-v2"),
    ComponentType
        .builder<EngineItemReferenceComponent>()
        .codec(
            RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.STRING.xmap(
                        { ItemId(it) },
                        { it.value }
                    )
                        .fieldOf("item")
                        .forGetter { it.id },
                    Codec.STRING.optionalFieldOf("uuid")
                        .xmap(
                            { it.map { ItemUuid(UUID.fromString(it).toString()) }.orElse(null) },
                            { Optional.ofNullable(it?.value) }
                        )
                        .forGetter { Optional.ofNullable(it.uuid).getOrNull() },

                    Codec.INT.optionalFieldOf("version", 0)
                        .forGetter { it.version }
                ).apply(instance) { id, uuid, version ->
                    EngineItemReferenceComponent(id, uuid, version)
                }
            }
        )
        .build()
)

// Вызываем ленивую инициализацию
fun initializeEngineItemComponents() = kotlin.Unit

const val CURRENT_ITEM_VERSION = 1

// 0 - до механики предметов, не нужно детачить
data class EngineItemReferenceComponent(val id: ItemId, val uuid: ItemUuid, val version: Int) {
    fun getItem(): EngineItem? {
        val itemStorage by injectItemAccess()
        return itemStorage.getItem(uuid)
    }
}

data class LegacyEngineItemReferenceComponent(val item: ItemId)

val ENGINE_ITEM_REFERENCE_COMPONENT_LEGACY: ComponentType<LegacyEngineItemReferenceComponent> = Registry.register(
    Registries.DATA_COMPONENT_TYPE,
    EngineId("reference-component"),
    ComponentType
        .builder<LegacyEngineItemReferenceComponent>()
        .codec(
            RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.STRING.xmap(
                        { ItemId(it) },
                        { it.value }
                    )
                        .fieldOf("item")
                        .forGetter { it.item }
                ).apply(
                    instance,
                    ::LegacyEngineItemReferenceComponent
                )
            }
        )
        .build()
)
