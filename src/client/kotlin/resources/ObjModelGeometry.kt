package org.lain.engine.client.resources

import de.javagl.obj.Mtl
import de.javagl.obj.Obj
import de.javagl.obj.ObjFace
import de.javagl.obj.ObjSplitting
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.model.MeshBakedGeometry
import net.fabricmc.fabric.impl.client.indigo.renderer.IndigoRenderer
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.renderer.block.model.TextureSlots
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.*
import net.minecraft.resources.Identifier
import org.joml.Vector3f
import org.lain.engine.mc.engineId

val ITEMS_ATLAS = TextureAtlas.LOCATION_ITEMS

class ObjGeometry(
    val obj: Obj,
    val mtl: Map<String, Mtl>,
    val flipV: Boolean
) : UnbakedGeometry {
    private val flg = if (flipV) MutableQuadView.BAKE_NORMALIZED or MutableQuadView.BAKE_FLIP_V else MutableQuadView.BAKE_NORMALIZED

    override fun bake(
        textures: TextureSlots,
        baker: ModelBaker,
        settings: ModelState,
        modelDebugName: ModelDebugName
    ): QuadCollection {
        var renderer = Renderer.get()

        if (renderer == null) {
            renderer = IndigoRenderer.INSTANCE;
        }

        val builder = renderer.mutableMesh()
        val emitter = builder.emitter()
        val spriteGetter = baker.sprites()
        val materialGroups = ObjSplitting.splitByMaterialGroups(obj)

        materialGroups.forEach { (name: String, objModel: Obj) ->
            val mtl = mtl[name]
            val sprite = mtl?.mapKd?.let { Material(ITEMS_ATLAS, engineId(it)) } ?: MISSING_SPRITE
            val emissive = name == "emissive"

            for (i in 0..<objModel.numFaces) {
                emitFace(
                    emitter,
                    settings,
                    spriteGetter,
                    modelDebugName,
                    sprite,
                    emissive,
                    objModel,
                    objModel.getFace(i)
                )
            }
        }

        return MeshBakedGeometry(builder.immutableCopy())
    }

    private fun emitFace(
        emitter: QuadEmitter,
        settings: ModelState,
        spriteGetter: SpriteGetter,
        modelDebugName: ModelDebugName,
        sprite: Material,
        emissive: Boolean,
        fObj: Obj,
        face: ObjFace
    ) {
        for (i in 0..<face.numVertices) {
            emitVertex(i, i, emitter, settings, emissive, fObj, face)
        }

        if (face.numVertices == 3) emitVertex(3, 2, emitter, settings, emissive, fObj, face)
        emitter.spriteBake(spriteGetter.get(sprite, modelDebugName), flg)
        emitter.color(-1, -1, -1, -1)
        emitter.emit()
    }

    private fun emitVertex(
        index: Int,
        vertexNum: Int,
        emitter: QuadEmitter,
        settings: ModelState,
        emissive: Boolean,
        fObj: Obj,
        face: ObjFace
    ) {
        val vt = fObj.getVertex(face.getVertexIndex(vertexNum))
        val vertex = Vector3f(vt.x, vt.y, vt.z)

        vertex.add(-0.5f, -0.5f, -0.5f)
        vertex.rotate(settings.transformation().leftRotation)
        vertex.add(0.5f, 0.5f, 0.5f)

        val normal = fObj.getNormal(face.getNormalIndex(vertexNum))
        val tex = fObj.getTexCoord(face.getTexCoordIndex(vertexNum))

        emitter
            .pos(index, vertex.x, vertex.y, vertex.z)
            .normal(index, normal.x, normal.y, normal.z)
            .uv(index, tex.x, tex.y)
            .emissive(emissive)
    }
}


class ObjUnbakedModel(
    val obj: Obj,
    val mtl: Map<String, Mtl>,
    val options: ObjModelOptions
): UnbakedModel {
    private val objGeometry = ObjGeometry(obj, mtl, options.flipV)
    override fun geometry(): UnbakedGeometry = objGeometry
    override fun transforms(): ItemTransforms = options.transforms
}

data class ObjModelOptions(
    val useAmbientOcclusion: Boolean,
    val guiLight: UnbakedModel.GuiLight?,
    val particle: Identifier?,
    val transforms: ItemTransforms,
    val flipV: Boolean,
    val mtlOverride: String?,
    val disableCulling: Boolean
)