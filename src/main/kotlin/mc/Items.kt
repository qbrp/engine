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
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.lain.engine.EngineMinecraftServer
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemNamespace
import org.lain.engine.item.ItemNamespaceId
import org.lain.engine.item.ItemUuid
import org.lain.engine.item.name
import org.lain.engine.util.EngineId
import org.lain.engine.util.injectItemStorage
import org.lain.engine.util.text.parseMiniMessage
import java.util.Optional
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

class EngineItemRegistry {
    // Список всех возможных идентификаторов (неймспейсов и предметов в неймспейсах). Для команд
    var identifiers: List<String> = listOf()
        private set
    var properties: Map<ItemId, ItemProperties> = mapOf()
        private set
    var namespaces: Map<ItemNamespaceId, ItemNamespace> = mapOf()
        private set

    fun upload(
        items: List<ItemProperties>,
        namespaces: List<ItemNamespace>
    ) {
        val ids = mutableListOf<String>()
        properties = items.associateBy {
            ids += it.id.value
            it.id
        }
        this.namespaces = namespaces.associateBy {
            ids += it.id.value
            it.id
        }
        identifiers = ids
    }
}

data class ItemListTab(
    val id: String,
    val name: String,
    val items: List<ItemId>
)

data class EngineItemContext(
    val itemRegistry: EngineItemRegistry,
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

fun bakeEngineItemStack(properties: ItemProperties, item: EngineItem): ItemStack {
    val materialStack = Registries.ITEM.get(properties.material).defaultStack ?: error("Item not found")
    materialStack.set(
        DataComponentTypes.ITEM_MODEL,
        properties.asset
    )
    materialStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    materialStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        properties.maxStackSize
    )
    properties.equipment?.let {
        materialStack.set(
            DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(it.slot)
                .allowedEntities(EntityType.PLAYER)
                .build()
        )
    }

    materialStack.set(
        EngineItemReferenceComponent.TYPE,
        EngineItemReferenceComponent(item.id, item.uuid, CURRENT_ITEM_VERSION)
    )
    updateEngineItemStack(materialStack, item)
    return materialStack
}

const val CURRENT_ITEM_VERSION = 1

// 0 - до механики предметов, не нужно детачить
data class EngineItemReferenceComponent(val id: ItemId, val uuid: ItemUuid?, val version: Int) {
    private var cachedItem: EngineItem? = null

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
                            Codec.STRING.optionalFieldOf("id")
                                .xmap(
                                    { it.map(::ItemId).orElse(ItemId("unknown")) },
                                    { Optional.of(it.value) }
                                )
                                .forGetter { Optional.of(it.id.value).getOrNull()?.let { ItemId(it) } },

                            Codec.STRING.optionalFieldOf("uuid")
                                .xmap(
                                    { it.map { ItemUuid(UUID.fromString(it)) }.orElse(null) },
                                    { Optional.ofNullable(it?.value?.toString()) }
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
