package org.lain.engine.client.resources

import com.google.gson.JsonObject
import de.javagl.obj.*
import net.minecraft.client.render.model.UnbakedModel.GuiLight
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.util.Identifier
import net.minecraft.util.JsonHelper
import org.lain.engine.client.mixin.resource.JsonUnbakedModelAccessor
import java.io.File
import java.io.IOException
/**
 * @param directory Родительская директория, в которой находится файл модели
 */
fun parseObjUnbakedModel(
    objModels: Map<String, EngineObjModel>,
    directory: File,
    identifier: Identifier,
    modelJson: JsonObject
): ObjUnbakedModel {
    val model = modelJson.getAsJsonPrimitive("model").asString
    // assets/tent/tent.json -> engine:~/tent -> engine:assets/tent/tent.obj
    val objModelPath = model.substituteEngineRelativePath(directory.path) + ".obj"
    val objModel = objModels[objModelPath] ?: error("Модель $objModelPath не найдена")

    return ObjUnbakedModel(identifier, objModel.obj, objModel.mtl, parseObjJsonModelOptions(modelJson))
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
    var transform = ModelTransformation.NONE

    if (modelJson.has("display")) {
        val jo = JsonHelper.asObject(modelJson, "display")
        transform = JsonUnbakedModelAccessor.`engine$getGson`().fromJson(jo, ModelTransformation::class.java)
    }

    var guiLight: GuiLight? = null
    if (modelJson.has("gui_light")) guiLight = GuiLight.byName(JsonHelper.asString(modelJson, "gui_light"))

    var particle: Identifier? = null
    if (modelJson.has("particle")) particle = Identifier.tryParse(JsonHelper.asString(modelJson, "particle"))

    val flipV = JsonHelper.getBoolean(modelJson, "flip_v", false)
    val mtlOverride = JsonHelper.getString(modelJson, "mtl_override", null)

    val useAmbientOcclusion = JsonHelper.getBoolean(modelJson, "ambientocclusion", true)

    return ObjModelOptions(
        useAmbientOcclusion,
        guiLight,
        particle,
        transform!!,
        flipV,
        mtlOverride,
    )
}