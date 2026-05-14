package org.lain.engine.client.render.item

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.lain.engine.client.mixin.render.ItemStackRenderStateAccessor
import org.lain.engine.player.Outfit

private val CULLING = RenderStateDataKey.create<Boolean> { "Engine culling" }

var ItemStackRenderState.culling: Boolean?
    get() = this.getData(CULLING)
    set(value) { setData(CULLING, value) }

private val TRANSFORMATION = RenderStateDataKey.create<ItemTransform> { "Engine transformation" }

var ItemStackRenderState.engineTransformation: ItemTransform?
    get() = this.getData(TRANSFORMATION)
    set(value) { setData(TRANSFORMATION, value) }

private val OUTFIT = RenderStateDataKey.create<Outfit> { "Engine outfit" }

var ItemStackRenderState.engineOutfit: Outfit?
    get() = this.getData(OUTFIT)
    set(value) { setData(OUTFIT, value) }

private val ADDITIONAL_TRANSFORMATION = RenderStateDataKey.create<ItemTransform> { "Engine Additional Transformations" }

fun ItemStackRenderState.LayerRenderState.setAdditionalTransformationsVanillaPipeline(
    transformations: EngineTransformationsBundle,
    context: ItemDisplayContext
) {
    setData(ADDITIONAL_TRANSFORMATION, transformations.minecraft().getTransform(context))
}

fun ItemStackRenderState.LayerRenderState.setAdditionalTransformationsEnginePipeline(
    transformations: EngineTransformationsBundle,
    context: EngineItemDisplayContext
) {
    setData(ADDITIONAL_TRANSFORMATION, transformations.getTransformation(context).minecraft())
}

fun ItemStackRenderState.LayerRenderState.getAdditionalTransformations(): ItemTransform? {
    return getData(ADDITIONAL_TRANSFORMATION)
}

fun ItemStackRenderState.setupAdditionalTransformationsVanilla(stack: ItemStack, displayContext: ItemDisplayContext) {
    val transformations = stack.get(DataComponents.ITEM_MODEL)?.let { AdditionalTransformationsBank.get(it) }
    if (transformations != null) {
        val layers = (this as ItemStackRenderStateAccessor).`engine$getLayers`()
        for (layer in layers) {
            layer.setAdditionalTransformationsVanillaPipeline(transformations, displayContext)
        }
    }
}

fun ItemStackRenderState.setupAdditionalTransformationsEngine(stack: ItemStack, displayContext: EngineItemDisplayContext) {
    val transformations = AdditionalTransformationsBank.get(stack.get(DataComponents.ITEM_MODEL)!!)
    if (transformations != null) {
        val layers = (this as ItemStackRenderStateAccessor).`engine$getLayers`()
        for (layer in layers) {
            layer.setAdditionalTransformationsEnginePipeline(transformations, displayContext)
        }
    }
}