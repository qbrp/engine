package org.lain.engine.client.mc.compat

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite

object AcceleratedMeshModels {
    // Allows comparing the two paths with -Dengine.disableAcceleratedMeshModels=true.
    val enabled: Boolean = FabricLoader.getInstance().isModLoaded("acceleratedrendering") &&
            !java.lang.Boolean.getBoolean("engine.disableAcceleratedMeshModels")
}

internal fun QuadEmitter.emitWithVanillaQuad(
    sprite: TextureAtlasSprite,
    quads: MutableList<BakedQuad>?,
) {
    if (quads != null) {
        val quad = toBakedQuad(sprite)
        if (material().emissive()) {
            // AR consumes the baked vertex lightmap instead of Fabric's material flags.
            for (vertex in 0 until 4) {
                quad.vertices[vertex * QuadView.VANILLA_VERTEX_STRIDE + 6] = LightTexture.FULL_BRIGHT
            }
        }
        quads += quad
    }
    emit()
}
