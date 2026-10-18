package org.lain.engine.client.resources

import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelState
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

class UnbakedMeshModel(val properties: PropModelProperties) : UnbakedModel {
    // сохраняем совместимость - этот API нам не нужен
    override fun getDependencies(): Collection<ResourceLocation> = emptyList()
    override fun resolveParents(function: Function<ResourceLocation, UnbakedModel>) = Unit

    override fun bake(
        modelBaker: ModelBaker, textureGetter: Function<Material, TextureAtlasSprite>, modelState: ModelState
    ): BakedModel {
        return bakeMeshModel(textureGetter, modelState)
    }
}