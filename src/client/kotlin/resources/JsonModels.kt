package org.lain.engine.client.resources

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.renderer.block.model.FaceBakery
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.Material
import net.minecraft.client.resources.model.ModelState
import net.minecraft.core.Direction
import org.lain.engine.client.mc.compat.emitWithVanillaQuad
import java.util.function.Function

private val FACE_BAKERY = FaceBakery()

fun MeshBuilder.buildJson(
    renderer: Renderer,
    textureGetter: Function<Material, TextureAtlasSprite>,
    geometry: PropModelProperties.Geometry.Json,
    state: ModelState,
    bakedQuads: MutableList<BakedQuad>? = null,
) {
    val model = geometry.blockModel
    val renderMaterial = renderer.materialFinder().find()
    val quadEmitter = emitter

    for (element in model.elements) {
        for ((direction, face) in element.faces) {
            val sprite = textureGetter.apply(model.getMaterial(face.texture))
            val quad = FACE_BAKERY.bakeQuad(
                element.from,
                element.to,
                face,
                sprite,
                direction,
                state,
                element.rotation,
                element.shade
            )
            val cullFace = face.cullForDirection?.let {
                Direction.rotate(state.rotation.matrix, it)
            }

            quadEmitter.fromVanilla(quad, renderMaterial, cullFace)

            val normal = quadEmitter.faceNormal()
            for (vertexIndex in 0 until 4) {
                quadEmitter.normal(vertexIndex, normal)
            }

            quadEmitter.emitWithVanillaQuad(sprite, bakedQuads)
        }
    }
}

fun Asset.parseBlockModel(json: JsonObject): BlockModel {
    val elements = json.getAsJsonArray("elements") ?: JsonArray()
    val textures = json.getAsJsonObject("textures") ?: JsonObject()
    textures.entrySet().forEach { (slot, id) ->
        textures.addProperty(slot, resolve(id.asString).toMinecraftId().toString())
    }

    val modelJson = JsonObject().apply {
        add("elements", elements)
        add("textures", textures)
    }
    return BlockModel.fromString(modelJson.toString())
}
