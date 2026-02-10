package org.lain.engine.client.mc.render

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.item.ItemGroup
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.registry.RegistryKey
import net.minecraft.text.Text
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mixin.ItemGroupAccessor
import org.lain.engine.mc.ENGINE_ITEM_INSTANTIATE_COMPONENT
import org.lain.engine.mc.wrapEngineItemStackBase
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
        results.items.forEach { (namespace, items) ->
            items.forEach { item ->
                val stack = item.properties.getMaterialStack()
                val properties = item.properties
                val prefab = item.prefab.properties
                wrapEngineItemStackBase(stack, properties.maxStackSize, properties.equipment)
                wrapEngineItemStackVisual(stack, prefab.name?.text ?: "Предмет", properties.asset)
                stack.set(ENGINE_ITEM_INSTANTIATE_COMPONENT, properties.id.value)
                entries.add(stack)
            }
        }
    }
}