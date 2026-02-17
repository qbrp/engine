package org.lain.engine.client.mc

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.text.Text
import org.lain.engine.client.mixin.ItemGroupAccessor
import org.lain.engine.mc.ENGINE_ITEM_INSTANTIATE_COMPONENT
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.wrapEngineItemStackVisual
import org.lain.engine.util.EngineId
import org.lain.engine.util.file.compileContents

private val KEY: RegistryKey<ItemGroup>? = RegistryKey.of(
    Registries.ITEM_GROUP.key,
    EngineId("item_group")
)

private val ITEM_GROUP = FabricItemGroup.builder()
    .icon { ItemStack(Items.MINECART) }
    .displayName(Text.of("Engine"))
    .build()

fun updateRandomEngineItemGroupIcon() {
    val stacks = ITEM_GROUP.displayStacks
    val itemGroup = ITEM_GROUP as ItemGroupAccessor
    if (stacks.isNotEmpty()) {
        itemGroup.`engine$setIcon`(stacks.random())
    }
}

fun updateEngineItemGroupEntries() {
    val client = MinecraftClient
    val featureSet = client.networkHandler?.enabledFeatures ?: return
    val lookup = client.world?.registryManager ?: return
    ITEM_GROUP.updateEntries(
        ItemGroup.DisplayContext(featureSet, false, lookup)
    )
}

fun registerEngineItemGroupEvent() {
    Registry.register(Registries.ITEM_GROUP, KEY, ITEM_GROUP);
    ItemGroupEvents.modifyEntriesEvent(KEY).register { entries ->
        val results = compileContents()
        results.namespaces.forEach { (_, namespace) ->
            namespace.items.forEach { (id, item) ->
                val stack = ITEM_STACK_MATERIAL
                val prefab = item.prefab.properties

                wrapEngineItemStackVisual(stack, prefab.name?.text ?: "Предмет")
                stack.set(ENGINE_ITEM_INSTANTIATE_COMPONENT, id.value)
                entries.add(stack)
            }
        }
    }
}