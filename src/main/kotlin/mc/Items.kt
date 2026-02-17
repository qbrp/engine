package org.lain.engine.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.EquippableComponent
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Unit
import org.lain.engine.item.*
import org.lain.engine.util.EngineId
import org.lain.engine.util.has
import org.lain.engine.util.injectItemAccess
import org.lain.engine.util.text.parseMiniMessage
import java.util.*
import kotlin.jvm.optionals.getOrNull

val ITEM_STACK_MATERIAL = ItemStack(Items.STICK)

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
    name: String
) {
    val currentName = itemStack.get(DataComponentTypes.ITEM_NAME)
    if (currentName?.string != name) {
        itemStack.set(
            DataComponentTypes.ITEM_NAME,
            Text.of(name)
        )
    }
}

fun wrapEngineItemStackBase(itemStack: ItemStack, maxStackSize: Int, hat: Boolean) {
    itemStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    itemStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        maxStackSize
    )
    if (hat) {
        itemStack.set(
            DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(EquipmentSlot.HEAD)
                .allowedEntities(EntityType.PLAYER)
                .build()
        )
    }
}

fun wrapEngineItemStack(
    item: EngineItem,
    itemStack: ItemStack
): ItemStack {
    wrapEngineItemStackVisual(itemStack, item.name)
    wrapEngineItemStackBase(itemStack, item.maxCount, item.has<Hat>())

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
