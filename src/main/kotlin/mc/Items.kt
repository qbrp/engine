package org.lain.engine.mc

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.util.Unit
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.item.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.storage.Uuid
import org.lain.engine.world.World
import java.util.*
import kotlin.jvm.optionals.getOrNull

val ITEM_STACK_MATERIAL = ItemStack(Items.STICK)

fun detachEngineItemStack(itemStack: ItemStack) {
    itemStack.remove(ENGINE_ITEM_REFERENCE_COMPONENT)
    itemStack.set(
        DataComponents.LORE,
        ItemLore(
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
    val currentName = itemStack.get(DataComponents.ITEM_NAME)
    if (currentName?.string != name) {
        itemStack.set(
            DataComponents.ITEM_NAME,
            name?.let { literalText(it) } ?: literalText("Предмет")
        )
    }
}

fun wrapEngineItemStackBase(itemStack: ItemStack, maxStackSize: Int) {
    itemStack.set(
        DataComponents.UNBREAKABLE,
        Unit.INSTANCE
    )
    itemStack.set(
        DataComponents.MAX_STACK_SIZE,
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
        EngineItemReferenceComponent(
            item.requireComponent<Item>().id,
            item.requireComponent<PersistentIdComponent>().id,
            CURRENT_ITEM_VERSION
        )
    )
    return itemStack
}

fun ItemStack.engine() = get(ENGINE_ITEM_REFERENCE_COMPONENT)

fun ItemStack.engineItem(world: World) = get(ENGINE_ITEM_REFERENCE_COMPONENT)?.getItem(world)

fun ItemStack.decrement(i: Int) = shrink(i)

fun ItemStack.increment(i: Int) = grow(i)

val ENGINE_ITEM_INSTANTIATE_COMPONENT: DataComponentType<String> = Registry.register(
    BuiltInRegistries.DATA_COMPONENT_TYPE,
    engineId("initialize-component"),
    DataComponentType
        .builder<String>()
        .persistent(Codec.STRING)
        .build()
)

val ENGINE_ITEM_REFERENCE_COMPONENT: DataComponentType<EngineItemReferenceComponent> = Registry.register(
    BuiltInRegistries.DATA_COMPONENT_TYPE,
    engineId("reference-component-v2"),
    DataComponentType
        .builder<EngineItemReferenceComponent>()
        .persistent(
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
                            { it.map { Uuid.from(it) as PersistentId }.orElse(null) },
                            { Optional.ofNullable(it.toString()) }
                        )
                        .forGetter { Optional.ofNullable(it.uuid).getOrNull() },

                    Codec.INT.optionalFieldOf("version", 0)
                        .forGetter { it.version }
                ).apply(instance) { id, uuid, version ->
                    EngineItemReferenceComponent(id, uuid, version, false)
                }
            }
        )
        .build()
)

// Вызываем ленивую инициализацию
fun initializeEngineItemComponents() = kotlin.Unit

const val CURRENT_ITEM_VERSION = 2

data class EngineItemReferenceComponent(
    val id: ItemId,
    val uuid: PersistentId,
    val version: Int,
    var loading: Boolean = false
) {
    fun getItem(world: World): EngineItem? {
        return world.itemStorage.get(uuid)
    }
}