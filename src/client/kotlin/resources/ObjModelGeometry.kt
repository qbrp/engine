package org.lain.engine.client.resources

import de.javagl.obj.Mtl
import de.javagl.obj.Obj
import de.javagl.obj.ObjFace
import de.javagl.obj.ObjSplitting
import net.fabricmc.fabric.api.renderer.v1.Renderer
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter
import net.fabricmc.fabric.api.renderer.v1.model.MeshBakedGeometry
import net.minecraft.client.render.model.*
import net.minecraft.client.render.model.UnbakedModel.GuiLight
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.client.util.SpriteIdentifier
import net.minecraft.util.Identifier
import org.joml.Vector3f
import org.lain.engine.util.EngineId

class ObjGeometry(
    val obj: Obj,
    val mtl: Map<String, Mtl>,
    val flipV: Boolean
) : Geometry {
    override fun bake(
        textures: ModelTextures,
        baker: Baker,
        settings: ModelBakeSettings,
        model: SimpleModel
    ): BakedGeometry {
        val renderer = Renderer.get()

        val builder = renderer.mutableMesh()
        val emitter = builder.emitter()
        val spriteGetter = baker.spriteGetter
        val materialGroups = ObjSplitting.splitByMaterialGroups(obj)

        materialGroups.forEach { (name: String, objModel: Obj) ->
            for (i in 0..<objModel.numFaces) {
                emitFace(
                    emitter,
                    settings,
                    spriteGetter,
                    model,
                    name,
                    objModel,
                    objModel.getFace(i)
                )
            }
        }

        return MeshBakedGeometry(builder.immutableCopy())
    }

    private fun emitFace(
        emitter: QuadEmitter,
        settings: ModelBakeSettings,
        spriteGetter: ErrorCollectingSpriteGetter,
        model: SimpleModel,
        materialName: String?,
        fObj: Obj,
        face: ObjFace
    ) {
        for (i in 0..<face.numVertices) {
            emitVertex(i, i, emitter, settings, fObj, face)
        }

        if (face.numVertices == 3) emitVertex(3, 2, emitter, settings, fObj, face)

        val smtl = mtl.get(materialName)
        var flg = MutableQuadView.BAKE_NORMALIZED
        if (flipV) flg = flg or MutableQuadView.BAKE_FLIP_V

        val textureId: Identifier? = smtl?.mapKd?.let { EngineId(it) }

        val sprite = textureId?.let { SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, textureId) } ?: MISSING_SPRITE

        emitter.spriteBake(spriteGetter.get(sprite, model), flg)
        emitter.color(-1, -1, -1, -1)
        emitter.emit()
    }

    private fun emitVertex(
        index: Int,
        vertexNum: Int,
        emitter: QuadEmitter,
        settings: ModelBakeSettings,
        fObj: Obj,
        face: ObjFace
    ) {
        val vt = fObj.getVertex(face.getVertexIndex(vertexNum))
        val vertex = Vector3f(vt.x, vt.y, vt.z)

        vertex.add(-0.5f, -0.5f, -0.5f)
        vertex.rotate(settings.rotation.leftRotation)
        vertex.add(0.5f, 0.5f, 0.5f)

        val normal = fObj.getNormal(face.getNormalIndex(vertexNum))
        val tex = fObj.getTexCoord(face.getTexCoordIndex(vertexNum))

        emitter
            .pos(index, vertex.x, vertex.y, vertex.z)
            .normal(index, normal.x, normal.y, normal.z)
            .uv(index, tex.x, tex.y)
    }
}


class ObjUnbakedModel(
    val obj: Obj,
    val mtl: Map<String, Mtl>,
    val options: ObjModelOptions
): UnbakedModel {
    private val objGeometry = ObjGeometry(obj, mtl, options.flipV)
    override fun geometry(): Geometry = objGeometry
}

data class ObjModelOptions(
    val useAmbientOcclusion: Boolean,
    val guiLight: GuiLight?,
    val particle: Identifier?,
    val transforms: ModelTransformation,
    val flipV: Boolean,
    val mtlOverride: String?,
    val disableCulling: Boolean
)