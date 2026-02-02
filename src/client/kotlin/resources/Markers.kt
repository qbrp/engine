package org.lain.engine.client.resources

import net.minecraft.client.network.AbstractClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.util.math.Vec3d
import org.joml.Vector3fc
import org.lain.engine.client.mc.MinecraftClient

fun ItemStack.getModelMarker(name: String): Vector3fc? {
    val itemModelId = get(DataComponentTypes.ITEM_MODEL)
    val itemModel = MinecraftClient.bakedModelManager.getItemModel(itemModelId) as? EngineItemModel ?: return null
    return itemModel.markers[name]
}