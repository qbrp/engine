package org.lain.engine.client.mc

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mixin.CreativeModeTabAccessor
import org.lain.engine.mc.ENGINE_ITEM_INSTANTIATE_COMPONENT
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.mc.engineId
import org.lain.engine.mc.literalText
import org.lain.engine.mc.wrapEngineItemStackBase
import org.lain.engine.mc.wrapEngineItemStackVisual

private val KEY: ResourceKey<CreativeModeTab> = ResourceKey.create(
    BuiltInRegistries.CREATIVE_MODE_TAB.key(),
    engineId("item_group")
)

private val ITEM_GROUP = FabricItemGroup.builder()
    .icon { ItemStack(Items.MINECART) }
    .title(literalText("Engine"))
    .build()

fun updateRandomEngineItemGroupIcon() {
    val stacks = ITEM_GROUP.displayItems
    val itemGroup = ITEM_GROUP as CreativeModeTabAccessor
    if (stacks.isNotEmpty()) {
        itemGroup.`engine$setIcon`(stacks.random())
    }
}

fun updateEngineItemGroupEntries() {
    val client = MinecraftClient
    val featureSet = client.connection?.enabledFeatures() ?: return
    val lookup = client.level?.registryAccess() ?: return
    ITEM_GROUP.buildContents(
        CreativeModeTab.ItemDisplayParameters(featureSet, false, lookup)
    )
}

fun registerEngineItemGroupEvent(client: EngineClient) {
    Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, ITEM_GROUP);
    ItemGroupEvents.modifyEntriesEvent(KEY).register { entries ->
        client.gameSession?.namespacedStorage?.items?.forEach { (id, prefab) ->
            val stack = ITEM_STACK_MATERIAL.copy()
            val assets = prefab.assets?.assets ?: return@forEach

            wrapEngineItemStackVisual(stack, prefab.name)
            wrapEngineItemStackBase(stack, prefab.maxCount)
            stack.set(
                DataComponents.ITEM_MODEL,
                engineId(assets["default"] ?: assets.toList().firstOrNull()?.second ?: "missingno")
            )
            stack.set(ENGINE_ITEM_INSTANTIATE_COMPONENT, id.value)
            entries.prepend(stack)
        }
    }
}