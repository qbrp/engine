package org.lain.engine.client.resources

import net.fabricmc.fabric.api.renderer.v1.RendererAccess
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelState
import org.lain.engine.client.mc.compat.AcceleratedMeshModels
import java.util.function.Function

fun UnbakedMeshModel.bakeMeshModel(
    textureGetter: Function<Material, TextureAtlasSprite>,
    state: ModelState
): MeshModel {
    val renderer = requireNotNull(RendererAccess.INSTANCE.renderer) {
        "Fabric renderer is unavailable while baking an Engine mesh model"
    }
    val builder = renderer.meshBuilder()
    val bakedQuads = if (AcceleratedMeshModels.enabled) mutableListOf<BakedQuad>() else null
    val particle = properties.particle?.let {
        textureGetter.apply(Material(ITEMS_ATLAS, it))
    }

    when (val geometry = properties.geometry) {
        is PropModelProperties.Geometry.Obj ->
            builder.buildObj(renderer, textureGetter, geometry.model, geometry.flipV, bakedQuads)

        is PropModelProperties.Geometry.Json ->
            builder.buildJson(renderer, textureGetter, geometry, state, bakedQuads)

        is PropModelProperties.Geometry.Glb -> TODO()
    }

    return MeshModel(
        builder.build(),
        particle ?: textureGetter.apply(MISSING_SPRITE),
        properties,
        bakedQuads?.toList().orEmpty(),
    )
}
