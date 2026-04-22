package org.lain.engine.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.component.ComponentType
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.text.Text
import net.minecraft.util.Unit
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.item.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.EngineId
import org.lain.engine.util.injectItemAccess
import org.lain.engine.util.text.parseMiniMessage
import org.lain.engine.world.World
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

context(world: World)
fun updateEngineItemStack(itemStack: ItemStack, item: EngineItem) {
    wrapEngineItemStackVisual(itemStack, item.getName())
}

fun wrapEngineItemStackVisual(
    itemStack: ItemStack,
    name: String? = null
) {
    val currentName = itemStack.get(DataComponentTypes.ITEM_NAME)
    if (currentName?.string != name) {
        itemStack.set(
            DataComponentTypes.ITEM_NAME,
            Text.of(name)
        )
    }
}

fun wrapEngineItemStackBase(itemStack: ItemStack, maxStackSize: Int) {
    itemStack.set(
        DataComponentTypes.UNBREAKABLE,
        Unit.INSTANCE
    )
    itemStack.set(
        DataComponentTypes.MAX_STACK_SIZE,
        maxStackSize
    )
}

context(world: World)
fun wrapEngineItemStack(
    item: EngineItem,
    itemStack: ItemStack
): ItemStack = with(world) {
    wrapEngineItemStackVisual(itemStack, item.getName())
    wrapEngineItemStackBase(itemStack, item.requireComponent<Count>().max)

    itemStack.set(
        ENGINE_ITEM_REFERENCE_COMPONENT,
        EngineItemReferenceComponent(item.requireComponent<ItemMeta>().id, item.requireComponent<PersistentId>(), CURRENT_ITEM_VERSION)
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
                            { it.map { PersistentId(it) }.orElse(null) },
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

data class EngineItemReferenceComponent(
    val id: ItemId,
    val uuid: PersistentId,
    val version: Int
) {
    fun getItem(): EngineItem? {
        val itemStorage by injectItemAccess()
        return itemStorage.getItem(uuid)
    }
}