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
import org.lain.engine.util.injectItemStorage
import org.lain.engine.util.text.parseMiniMessage
import java.util.Optional
import java.util.UUID
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
)

@Serializable
data class ItemEquipment(val slot: EquipmentSlot)

fun detachEngineItemStack(itemStack: ItemStack) {
    itemStack.remove(EngineItemReferenceComponent.TYPE)
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
    itemStack.set(
        DataComponentTypes.ITEM_NAME,
        Text.of(item.name)
    )
}

fun bakeEngineItemStack(
    properties: ItemProperties,
    item: EngineItem,
    itemStack: ItemStack
): ItemStack {
    itemStack.set(
        DataComponentTypes.ITEM_NAME,
        Text.of(item.name)
    )
    itemStack.set(
        DataComponentTypes.ITEM_MODEL,
        properties.asset
    )
    itemStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    itemStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        properties.maxStackSize
    )
    properties.equipment?.let {
        itemStack.set(
            DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(it.slot)
                .allowedEntities(EntityType.PLAYER)
                .build()
        )
    }

    itemStack.set(
        EngineItemReferenceComponent.TYPE,
        EngineItemReferenceComponent(item.id, item.uuid, CURRENT_ITEM_VERSION)
    )
    updateEngineItemStack(itemStack, item)
    return itemStack
}

fun ItemStack.engine() = get(EngineItemReferenceComponent.TYPE)

fun ItemStack.engineItem() = get(EngineItemReferenceComponent.TYPE)?.getItem()

const val CURRENT_ITEM_VERSION = 1

// 0 - до механики предметов, не нужно детачить
data class EngineItemReferenceComponent(val id: ItemId, val uuid: ItemUuid?, val version: Int) {
    var cachedItem: EngineItem? = null

    fun getItem(): EngineItem? {
        if (uuid == null) return null
        return cachedItem ?: run {
            val itemStorage by injectItemStorage()
            val item = itemStorage.get(uuid!!)
            cachedItem = item
            item
        }
    }

    companion object {
        // Вызываем ленивую инициализацию
        fun initialize() = kotlin.Unit

        val TYPE: ComponentType<EngineItemReferenceComponent> = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            EngineId("reference-component"),
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
    }
}
