package org.lain.engine.client.resources

import de.javagl.obj.*
import org.lain.engine.script.EngineId
import java.io.IOException
import kotlin.collections.component1
import kotlin.collections.component2

data class ParsedObj(
    val model: Obj,
    val materialLibrariesRefs: List<MtlRef>
) {
    sealed interface MtlRef {
        data class Slot(val name: String) : MtlRef
        data class Direct(val id: EngineId) : MtlRef
    }
}

data class ParsedMtlLibrary(
    val materials: List<ParsedMtlMaterial>
)

data class ParsedMtlMaterial(
    val source: Mtl,
    val diffuseTexture: EngineId
)

fun Asset.parseObjModel(): ParsedObj {
    return source.read { reader ->
        val obj = ObjUtils.convertToRenderable(ObjReader.read(reader))
        ParsedObj(obj, obj.mtlFileNames.map {
            if (it.startsWith("#")) {
                ParsedObj.MtlRef.Slot(it.drop(1))
            } else {
                ParsedObj.MtlRef.Direct(resolve(it))
            }
        })
    }
}

fun Asset.parseMtlLibrary(): ParsedMtlLibrary {
    val mtlDefinitions = source.read { MtlReader.read(it) }
    val materials = mtlDefinitions.map { mtl ->
        ParsedMtlMaterial(
            mtl,
            resolve(mtl.mapKd ?: error("mapKd не указан"))
        )
    }
    return ParsedMtlLibrary(materials)
}
