package org.lain.engine.client.resources

import com.google.gson.JsonObject
import de.javagl.obj.Mtl
import de.javagl.obj.MtlReader
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.Identifier
import org.lain.engine.client.mc.JsonMc
import org.lain.engine.client.mixin.resource.JsonUnbakedModelAccessor
import org.lain.engine.mc.parseId
import java.io.File
import java.io.IOException

/**
 * @param directory Родительская директория, в которой находится файл модели
 */
fun parseObjUnbakedModel(
    objModels: Map<String, EngineObjModel>,
    directory: File,
    modelJson: JsonObject
): ObjUnbakedModel {
    val model = modelJson.getAsJsonPrimitive("model").asString
    // assets/tent/tent.json -> engine:~/tent -> engine:assets/tent/tent.obj
    val objModelPath = model.substituteEngineRelativePath(directory.path) + ".obj"
    val objModel = objModels[objModelPath] ?: error("Модель $objModelPath не найдена")

    return ObjUnbakedModel(objModel.obj, objModel.mtl, parseObjJsonModelOptions(modelJson))
}

fun parseObjModel(asset: Asset): EngineObjModel {
    val file = asset.source.file
    val reader = file.reader()
    try {
        reader.use { reader ->
            val obj = ObjUtils.convertToRenderable(ObjReader.read(reader))
            val mtlFileNames = obj.mtlFileNames
            val mtlDirPaths = file.path
                .split('/')
                .filter { it.isNotEmpty() }
                .toMutableList()
            mtlDirPaths.removeAt(mtlDirPaths.size - 1)

            val mtl = loadMtl(file.parentFile, mtlFileNames)

            return EngineObjModel(asset, obj, mtl)
        }
    } catch (e: IOException) {
        throw IllegalStateException("Failed to load obj file.", e)
    }
}

fun loadMtl(
    directory: File,
    mtls: List<String>
): MutableMap<String, Mtl> {
    return mtls
        .flatMap { r -> loadMtl(directory, r) }
        .associateBy { it.name }
        .toMutableMap()
}

fun loadMtl(directory: File, mtlName: String): List<Mtl> {
    val mtlFile = directory.resolve(mtlName)
    return try {
        mtlFile.reader().use { reader -> MtlReader.read(reader) }
    } catch (e: IOException) {
        LOGGER.error("Failed to read mtl file.", e)
        emptyList()
    }
}

fun parseObjJsonModelOptions(modelJson: JsonObject): ObjModelOptions {
    var transform = ItemTransforms.NO_TRANSFORMS

    if (modelJson.has("display")) {
        val jo = modelJson.getAsJsonObject("display")
        transform = JsonUnbakedModelAccessor.`engine$getGson`().fromJson(jo, ItemTransforms::class.java)
    }

    var guiLight: UnbakedModel.GuiLight? = null
    if (modelJson.has("gui_light")) guiLight = UnbakedModel.GuiLight.getByName(
        JsonMc.getAsString(modelJson, "gui_light")
    )

    var particle: Identifier? = null
    if (modelJson.has("particle")) particle = parseId(JsonMc.getAsString(modelJson, "particle"))

    val flipV = JsonMc.getAsBoolean(modelJson, "flip_v", false)
    val mtlOverride = JsonMc.getAsString(modelJson, "mtl_override", null)

    val useAmbientOcclusion = JsonMc.getAsBoolean(modelJson, "ambient_occlusion", true)
    val disableCulling = JsonMc.getAsBoolean(modelJson, "disable_culling", false)

    return ObjModelOptions(
        useAmbientOcclusion,
        guiLight,
        particle,
        transform!!,
        flipV,
        mtlOverride,
        disableCulling
    )
}