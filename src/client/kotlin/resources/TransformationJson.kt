package org.lain.engine.client.resources

import com.google.gson.*
import net.minecraft.client.render.model.json.Transformation
import net.minecraft.util.JsonHelper
import net.minecraft.util.math.MathHelper
import org.joml.Vector3f
import org.lain.engine.client.mc.render.EngineTransformation
import org.lain.engine.client.mc.render.Transformations
import org.lain.engine.client.mc.render.isIdentity
import org.lain.engine.client.mc.render.isZero
import org.lain.engine.util.math.MutableVec3
import java.io.File

val DEFAULT_ROTATION = Vector3f(0.0f, 0.0f, 0.0f)
val DEFAULT_TRANSLATION = Vector3f(0.0f, 0.0f, 0.0f)
val DEFAULT_SCALE = Vector3f(1.0f, 1.0f, 1.0f)

fun parseTransformation(jsonElement: JsonElement): Transformation {
    val jsonObject = jsonElement.getAsJsonObject()
    val vector3f: Vector3f = parseVector3f(jsonObject, "rotation", DEFAULT_ROTATION)
    val vector3f2: Vector3f = parseVector3f(jsonObject, "translation", DEFAULT_TRANSLATION)
    vector3f2.mul(0.0625f)
    vector3f2.set(
        MathHelper.clamp(vector3f2.x, -5.0f, 5.0f),
        MathHelper.clamp(vector3f2.y, -5.0f, 5.0f),
        MathHelper.clamp(vector3f2.z, -5.0f, 5.0f)
    )
    val vector3f3: Vector3f = parseVector3f(jsonObject, "scale", DEFAULT_SCALE)
    vector3f3.set(
        MathHelper.clamp(vector3f3.x, -4.0f, 4.0f),
        MathHelper.clamp(vector3f3.y, -4.0f, 4.0f),
        MathHelper.clamp(vector3f3.z, -4.0f, 4.0f)
    )
    return Transformation(vector3f, vector3f2, vector3f3)
}

fun parseVector3f(json: JsonObject, key: String, fallback: Vector3f): Vector3f {
    if (!json.has(key)) {
        return fallback
    }
    val jsonArray = JsonHelper.getArray(json, key)
    if (jsonArray.size() != 3) {
        throw JsonParseException("Expected 3 " + key + " values, found: " + jsonArray.size())
    }
    val fs = FloatArray(3)
    for (i in fs.indices) {
        fs[i] = JsonHelper.asFloat(jsonArray.get(i), ("$key[$i]"))
    }
    return Vector3f(fs[0], fs[1], fs[2])
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

fun MutableVec3.toJsonArray(divide: Float = 1f): JsonArray {
    val array = JsonArray()
    array.add(x / divide)
    array.add(y / divide)
    array.add(z / divide)
    return array
}

fun JsonObject.addIfNotIdentity(name: String, transformation: EngineTransformation) {
    if (!transformation.isIdentity()) {
        add(name, transformation.toJson())
    }
}

fun Transformations.toDisplayJson(): JsonObject {
    val display = JsonObject()

    display.addIfNotIdentity("thirdperson_righthand", thirdPersonRightHand)
    display.addIfNotIdentity("thirdperson_lefthand", thirdPersonLeftHand)
    display.addIfNotIdentity("firstperson_righthand", firstPersonRightHand)
    display.addIfNotIdentity("firstperson_lefthand", firstPersonLeftHand)
    display.addIfNotIdentity("head", head)
    display.addIfNotIdentity("gui", gui)
    display.addIfNotIdentity("ground", ground)
    display.addIfNotIdentity("fixed", fixed)

    return display
}

fun Transformations.toAssetJson(jsonObject: JsonObject) {
    jsonObject.addIfNotIdentity("transformation_outfit", outfit)
}

private fun File.withExtension(newExt: String): File {
    val nameWithoutExt = this.nameWithoutExtension.replace(".asset", "")
    val parentDir = this.parentFile
    val cleanExt = newExt.removePrefix(".")
    return File(parentDir, "$nameWithoutExt.$cleanExt")
}

fun exportEngineModelTransformations(model: EngineItemModel, transformations: Transformations) {
    val assetSource = model.asset.source
    val modelSource = assetSource.file
        .withExtension("json")

    modelSource.reader().use { reader ->
        val json = JsonHelper.deserialize(reader).asJsonObject
        json.add(
            "display",
            transformations.toDisplayJson()
        )
        modelSource.writeText(Gson().toJson(json))
    }

    assetSource.file.reader().use { reader ->
        val json = JsonHelper.deserialize(reader).asJsonObject
        transformations.toAssetJson(json)
        assetSource.file.writeText(Gson().toJson(json))
    }
}