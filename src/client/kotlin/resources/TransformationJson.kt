package org.lain.engine.client.resources

import com.google.gson.*
import org.lain.engine.client.mc.JsonMc
import org.lain.engine.client.render.item.EngineTransformation
import org.lain.engine.client.render.item.EngineTransformationsBundle
import org.lain.engine.mc.MathMc
import org.lain.engine.util.math.MutableEVec3

val DEFAULT_ROTATION = MutableEVec3(0.0f, 0.0f, 0.0f)
val DEFAULT_TRANSLATION = MutableEVec3(0.0f, 0.0f, 0.0f)
val DEFAULT_SCALE = MutableEVec3(1.0f, 1.0f, 1.0f)

fun parseTransformation(jsonElement: JsonElement): EngineTransformation {
    val jsonObject = jsonElement.getAsJsonObject()
    val rotation = parseEngineVector(jsonObject, "rotation", DEFAULT_ROTATION)
    val translation = parseEngineVector(jsonObject, "translation", DEFAULT_TRANSLATION)
    translation.set(
        MathMc.clamp(translation.x * 0.0625f, -5.0f, 5.0f),
        MathMc.clamp(translation.y * 0.0625f, -5.0f, 5.0f),
        MathMc.clamp(translation.z * 0.0625f, -5.0f, 5.0f)
    )
    val scale = parseEngineVector(jsonObject, "scale", DEFAULT_SCALE)
    scale.set(
        MathMc.clamp(scale.x, -4.0f, 4.0f),
        MathMc.clamp(scale.y, -4.0f, 4.0f),
        MathMc.clamp(scale.z, -4.0f, 4.0f)
    )
    return EngineTransformation(translation, rotation, scale)
}

fun parseEngineVector(json: JsonObject, key: String, fallback: MutableEVec3): MutableEVec3 {
    if (!json.has(key)) {
        return MutableEVec3(fallback)
    }
    val jsonArray = JsonMc.getAsJsonArray(json, key)
    if (jsonArray.size() != 3) {
        throw JsonParseException("Expected 3 " + key + " values, found: " + jsonArray.size())
    }
    val fs = FloatArray(3)
    for (i in fs.indices) {
        fs[i] = jsonArray.get(i).asFloat
    }
    return MutableEVec3(fs[0], fs[1], fs[2])
}

fun EngineTransformation.toJson(): JsonObject {
    val json = JsonObject()

    if (!translation.isZero()) {
        json.add("translation", translation.toJsonArray(0.0625f))
    }

    if (!rotation.isZero()) {
        json.add("rotation", rotation.toJsonArray())
    }

    if (!scale.isZero()) {
        json.add("scale", scale.toJsonArray())
    }

    return json
}

fun MutableEVec3.toJsonArray(divide: Float = 1f): JsonArray {
    val array = JsonArray()
    array.add(x / divide)
    array.add(y / divide)
    array.add(z / divide)
    return array
}

fun JsonObject.addIfNotIdentity(name: String, transformation: EngineTransformation) {
    if (!transformation.isIdentity) {
        add(name, transformation.toJson())
    }
}

fun EngineTransformationsBundle.toDisplayJson(): JsonObject {
    val display = JsonObject()

    display.addIfNotIdentity("thirdperson_righthand", thirdPersonRightHand)
    display.addIfNotIdentity("thirdperson_lefthand", thirdPersonLeftHand)
    display.addIfNotIdentity("firstperson_righthand", firstPersonRightHand)
    display.addIfNotIdentity("firstperson_lefthand", firstPersonLeftHand)
    display.addIfNotIdentity("head", head)
    display.addIfNotIdentity("gui", gui)
    display.addIfNotIdentity("ground", ground)
    display.addIfNotIdentity("fixed", fixed)
    display.addIfNotIdentity("outfit", outfit)

    return display
}

fun exportEngineModelTransformations(model: MeshModel, transformations: EngineTransformationsBundle) {
    val modelSource = model.properties.asset.source

    val json = modelSource.read { reader ->
        val json = JsonMc.parse(reader).asJsonObject
        json.add(
            "display",
            transformations.toDisplayJson()
        )
        json
    }

    modelSource.file.writeText(Gson().toJson(json))
}
