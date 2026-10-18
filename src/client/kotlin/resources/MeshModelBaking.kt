package org.lain.engine.client.resources

import net.fabricmc.fabric.api.renderer.v1.RendererAccess
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelState
import java.util.function.Function

fun UnbakedMeshModel.bakeMeshModel(
    textureGetter: Function<Material, TextureAtlasSprite>,
    state: ModelState
): MeshModel {
    val renderer = requireNotNull(RendererAccess.INSTANCE.renderer) {
        "Fabric renderer is unavailable while baking an Engine mesh model"
    }
    val builder = renderer.meshBuilder()
    val particle = properties.particle?.let {
        textureGetter.apply(Material(ITEMS_ATLAS, it))
    }

    when (val geometry = properties.geometry) {
        is PropModelProperties.Geometry.Obj ->
            builder.buildObj(renderer, textureGetter, geometry.model, geometry.flipV)

        is PropModelProperties.Geometry.Json ->
            builder.buildJson(renderer, textureGetter, geometry, state)

        is PropModelProperties.Geometry.Glb -> TODO()
    }

    return MeshModel(
        builder.build(),
        particle ?: textureGetter.apply(MISSING_SPRITE),
        properties
    )
}
