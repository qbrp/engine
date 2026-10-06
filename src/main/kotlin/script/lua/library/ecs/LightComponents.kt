package org.lain.engine.script.lua.library.ecs

import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.world.LightBehaviour
import org.lain.engine.world.LightSource
import org.lain.engine.world.Luminance
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable

private fun LuaTable.toLightBehaviour(): LightBehaviour {
    val parameters = get("params").checktable()
    return when (val type = get("type").tojstring()) {
        "sphere" -> LightBehaviour.Sphere(parameters.get("radius").toint())
        else -> error("Unsupported light behaviour type: $type")
    }
}

fun World.applyLuaLightComponents() {
    projectLuaComponent<LightSource>(CoreScriptComponents.LIGHT_SOURCE) { value, _ ->
        val behaviour = value.get("behaviour").checktable()
        LightSource(behaviour.toLightBehaviour())
    }
    projectLuaComponent<Luminance>(CoreScriptComponents.LUMINANCE) { value, _ ->
        Luminance(value.get("level").toint())
    }
}
