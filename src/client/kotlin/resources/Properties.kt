package org.lain.engine.client.resources

import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3fc
import org.lain.engine.client.render.item.AdditionalTransformationsBank
import org.lain.engine.client.render.item.minecraft

data class EngineItemDefinition(
    val asset: Asset,
    val model: ResourceLocation,
    val disableCulling: Boolean,
    val markers: Map<String, Vector3fc>,
    val outfitTransformation: ItemTransform?
)

interface EngineModelMetadata {
    val disableCulling: Boolean
}

class EngineItemModel(
    val id: ResourceLocation,
    val asset: Asset,
    val wrapped: BakedModel,
    definitionDisableCulling: Boolean,
    val markers: Map<String, Vector3fc>,
    val outfitTransformation: ItemTransform?
) : ForwardingBakedModel(), EngineModelMetadata {
    override fun getWrappedModel(): BakedModel = wrapped
    val baseTransforms: ItemTransforms = wrapped.transforms

    override val disableCulling: Boolean =
        definitionDisableCulling || (wrapped as? EngineModelMetadata)?.disableCulling == true

    override fun getTransforms(): ItemTransforms {
        return AdditionalTransformationsBank.get(id)?.minecraft() ?: baseTransforms
    }
}
