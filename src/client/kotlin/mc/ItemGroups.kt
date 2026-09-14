package org.lain.engine.client.mc

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.mixin.CreativeModeTabAccessor
import org.lain.engine.mc.*
import org.lain.engine.mc.ecs.ENGINE_ITEM_INSTANTIATE_COMPONENT
import org.lain.engine.mc.ecs.ENGINE_ITEM_MODEL_COMPONENT
import org.lain.engine.mc.ecs.ITEM_STACK_MATERIAL
import org.lain.engine.mc.ecs.wrapEngineItemStackBase
import org.lain.engine.mc.ecs.wrapEngineItemStackVisual

private val KEY: ResourceKey<CreativeModeTab> = ResourceKey.create(
    BuiltInRegistries.CREATIVE_MODE_TAB.key(),
    engineId("item_group")
)

private val ITEM_GROUP = FabricItemGroup.builder()
    .icon { ItemStack(Items.MINECART) }
    .title(literalText("Engine"))
    .build()

fun updateRandomEngineItemGroupIcon(client: Minecraft) {
    if (client.player?.containerMenu !is CreativeModeInventoryScreen.ItemPickerMenu) return
    val stacks = ITEM_GROUP.displayItems.filter {
        val itemModel = it.get(ENGINE_ITEM_MODEL_COMPONENT) ?: return@filter false
        client.modelManager.getModel(itemModel) !== client.modelManager.missingModel
    }
    val itemGroup = ITEM_GROUP as CreativeModeTabAccessor
    if (stacks.isNotEmpty()) {
        itemGroup.`engine$setIcon`(stacks.random())
    }
}

private var currentGameSession: GameSession? = null

fun updateEngineItemGroupEntries(gameSession: GameSession) {
    currentGameSession = gameSession
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
        val gameSession = currentGameSession ?: return@register
        gameSession.inventoryTab.entries
            .mapNotNull { gameSession.namespacedStorage.items[it.prefabId] }
            .forEach { prefab ->
                val stack = ITEM_STACK_MATERIAL.copy()
                val assets = prefab.assets?.assets ?: return@forEach

                wrapEngineItemStackVisual(stack, prefab.name)
                wrapEngineItemStackBase(stack, prefab.maxCount)
                stack.set(
                    ENGINE_ITEM_MODEL_COMPONENT,
                    engineId(
                        assets["default"]?.full ?: assets.toList().firstOrNull()?.second?.full
                        ?: "missingno"
                    )
                )
                stack.set(ENGINE_ITEM_INSTANTIATE_COMPONENT, prefab.id.toString())
                entries.prepend(stack)
            }
    }
}
