package org.lain.engine.client.resources

import com.google.gson.JsonObject
import net.minecraft.client.renderer.block.model.BlockModel
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.resources.ResourceLocation
import org.lain.engine.client.mc.JsonMc
import org.lain.engine.client.mixin.resource.JsonUnbakedModelAccessor
import org.lain.engine.client.render.item.EngineTransformationsBundle
import org.lain.engine.client.render.item.engine
import org.lain.engine.mc.parseId
import org.lain.engine.script.EngineId

data class PropModelProperties(
    val asset: Asset,
    val model: ResourceLocation,
    val particle: ResourceLocation?,
    val useAmbientOcclusion: Boolean,
    val disableCulling: Boolean,
    val flatLight: Boolean,
    val transform: EngineTransformationsBundle,
    val rotateLeftSide: Boolean,
    val mirrorX: Boolean
)

data class ParsedPropModel(
    val properties: PropModelProperties,
    val geometry: Geometry
) {
    sealed class Geometry {
        data class Json(val blockModel: BlockModel) : Geometry() // геометрия описывается в самом Json-файле квадами
        data class Obj(
            val model: EngineId,
            val mtlOverride: Map<String, EngineId>,
            val flipV: Boolean,
        ) : Geometry()
    }
}

data class LinkedPropModel(
    val properties: PropModelProperties,
    val geometry: Geometry
) {
    sealed class Geometry {
        data class Json(val blockModel: BlockModel) : Geometry() // геометрия описывается в самом Json-файле квадами
        data class Obj(val linkedObj: LinkedObj, val flipV: Boolean) : Geometry()
    }
}

fun Asset.parsePropModel(): ParsedPropModel {
    var transform: EngineTransformationsBundle? = null

    val modelJson = source.openInputStream().use {
        it.reader().use { reader ->
            JsonMc.parse(reader)
        }
    }
    val registrationId = registrationId

    if (modelJson.has("display")) {
        // костыльно читаем трансформации как ItemTransforms через майнкрафтовский GSON, после чего переводим их
        // в EngineTransformationsBundle. В противном случае пришлось бы писать ещё больше кода
        val displayJson = modelJson.getAsJsonObject("display")
        val itemTransform =
            JsonUnbakedModelAccessor.`engine$getGson`().fromJson(displayJson, ItemTransforms::class.java)
        val outfitTransform = JsonMc.getAsJsonObject(displayJson, "outfit", null)?.let { parseTransformation(it) }
        transform = itemTransform.engine(outfitTransform)
    }

    val flatLight = JsonMc.getAsBoolean(modelJson, "flat_light", false)

    val particle = JsonMc.getAsString(modelJson, "particle", null)?.let { parseId(it) }

    val useAmbientOcclusion = JsonMc.getAsBoolean(modelJson, "ambient_occlusion", true)
    val disableCulling = JsonMc.getAsBoolean(modelJson, "disable_culling", false)

    val geometry = parseLegacyObjGeometry(modelJson) ?: run {
        JsonMc.getAsJsonObject(modelJson, "geometry", null)?.let { geometryJson ->
            val geometryType = JsonMc.getAsString(geometryJson, "type")

            when (geometryType) {
                "json" -> ParsedPropModel.Geometry.Json(parseBlockModel(modelJson))
                "obj" -> parseObjGeometry(geometryJson)
                "glb" -> TODO()

                else -> error("Unknown geometry type: $geometryType")
            }
        } ?: ParsedPropModel.Geometry.Json(parseBlockModel(modelJson))
    }

    val rotateLeftSide = JsonMc.getAsBoolean(modelJson, "rotate_left_side", false)
    val mirrorX = JsonMc.getAsBoolean(modelJson, "mirror_x", false)

    val properties = PropModelProperties(
        this,
        registrationId,
        particle,
        useAmbientOcclusion,
        disableCulling,
        flatLight,
        transform ?: EngineTransformationsBundle(),
        rotateLeftSide,
        mirrorX
    )
    return ParsedPropModel(properties, geometry)
}

fun Asset.parseLegacyObjGeometry(modelJson: JsonObject): ParsedPropModel.Geometry.Obj? {
    val isObj = modelJson.has("obj") && modelJson.getAsJsonPrimitive("obj").asBoolean
    return if (isObj) {
        parseObjGeometry(modelJson)
    } else {
        null
    }
}

fun Asset.parseObjGeometry(modelJson: JsonObject): ParsedPropModel.Geometry.Obj {
    val flipV = JsonMc.getAsBoolean(modelJson, "flip_v", false)
    val mtlOverride =
        JsonMc.getAsJsonObject(modelJson, "mtl_override", JsonObject())
            ?.asMap()
            ?.mapValues { resolve(it.value.asString) }
            ?: emptyMap()
    val path = resolve(JsonMc.getAsString(modelJson, "model"))
    return ParsedPropModel.Geometry.Obj(path, mtlOverride, flipV)
}