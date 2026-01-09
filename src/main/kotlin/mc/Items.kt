package org.lain.engine.mc

import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.Decoder
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.Encoder
import com.mojang.serialization.codecs.RecordCodecBuilder
import kotlinx.serialization.Serializable
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.EquippableComponent
import net.minecraft.entity.EntityType
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Unit
import org.lain.engine.util.EngineId

data class EngineItem(
    val id: ItemId,
    val name: Text,
    val material: Identifier,
    val asset: Identifier,
    val maxStackSize: Int,
    val equipment: ItemEquipment? = null
)

@Serializable
data class ItemEquipment(val slot: EquipmentSlot)

fun getItemStack(item: EngineItem): ItemStack {
    val materialStack = Registries.ITEM.get(item.material).defaultStack ?: error("Item not found")
    materialStack.set(
        DataComponentTypes.ITEM_MODEL,
        item.asset
    )
    materialStack.set(
        DataComponentTypes.ITEM_NAME,
        item.name
    )
    materialStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    materialStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        item.maxStackSize
    )
    item.equipment?.let {
        materialStack.set(
            DataComponentTypes.EQUIPPABLE,
            EquippableComponent.builder(it.slot)
                .allowedEntities(EntityType.PLAYER)
                .build()
        )
    }
    materialStack.set(
        EngineItemReferenceComponent.TYPE,
        EngineItemReferenceComponent(item.id)
    )
    return materialStack
}

class EngineItemReferenceComponent(val item: ItemId) {
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
                                .forGetter { it.item }
                        ).apply(
                            instance,
                            ::EngineItemReferenceComponent
                        )
                    }
                )
                .build()
        )
    }
}