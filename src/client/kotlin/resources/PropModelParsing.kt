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
    val geometry: Geometry,
    val rotateLeftSide: Boolean,
    val mirrorX: Boolean
) {
    sealed class Geometry {
        data class Json(val blockModel: BlockModel) : Geometry() // геометрия описывается в самом Json-файле квадами
        data class Obj(val model: LinkedObj, val flipV: Boolean) : Geometry()
        data class Glb(val path: LinkedObj) : Geometry()
    }
}

fun Asset.parsePropModel(linkedObjs: Map<EngineId, LinkedObj>): PropModelProperties {
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

    val geometry = parseLegacyObjGeometry(modelJson, linkedObjs) ?: run {
        JsonMc.getAsJsonObject(modelJson, "geometry", null)?.let { geometryJson ->
            val geometryType = JsonMc.getAsString(geometryJson, "type")

            fun parseGeometryPath() = resolve(JsonMc.getAsString(geometryJson, "path"))

            when (geometryType) {
                "json" -> PropModelProperties.Geometry.Json(parseBlockModel(modelJson))

                "obj" -> {
                    val flipV = JsonMc.getAsBoolean(geometryJson, "flip_v", false)
                    val geometryId = parseGeometryPath()
                    PropModelProperties.Geometry.Obj(
                        linkedObjs[geometryId] ?: error("Геометрия не найдена: $geometryId"),
                        flipV
                    )
                }

                "glb" -> {
                    val geometryId = parseGeometryPath()
                    PropModelProperties.Geometry.Glb(TODO())
                }

                else -> error("Unknown geometry type: $geometryType")
            }
        } ?: PropModelProperties.Geometry.Json(parseBlockModel(modelJson))
    }

    val rotateLeftSide = JsonMc.getAsBoolean(modelJson, "rotate_left_side", false)
    val mirrorX = JsonMc.getAsBoolean(modelJson, "mirror_x", false)

    return PropModelProperties(
        this,
        registrationId,
        particle,
        useAmbientOcclusion,
        disableCulling,
        flatLight,
        transform ?: EngineTransformationsBundle(),
        geometry,
        rotateLeftSide,
        mirrorX
    )
}

fun Asset.parseLegacyObjGeometry(
    modelJson: JsonObject,
    linkedObjs: Map<EngineId, LinkedObj>,
): PropModelProperties.Geometry.Obj? {
    val isObj = modelJson.has("obj") && modelJson.getAsJsonPrimitive("obj").asBoolean
    val flipV = JsonMc.getAsBoolean(modelJson, "flip_v", false)
    return if (isObj) {
        val path = resolve(JsonMc.getAsString(modelJson, "model"))
        PropModelProperties.Geometry.Obj(
            linkedObjs[path] ?: error("Геометрия не найдена: $path"),
            flipV,
        )
    } else {
        null
    }
}
