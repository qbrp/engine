package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.iterate
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.castedLuaValue
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
    val luminanceArray = componentManager.getComponentArray<Luminance>()
    val lightSourceArray = componentManager.getComponentArray<LightSource>()
    iterate(CoreScriptComponents.LIGHT_SOURCE) { entity, lightSource ->
        val behaivour = lightSource.castedLuaValue.get("behaviour").checktable()
        lightSourceArray.getOrSet(entity) { LightSource(behaivour.toLightBehaviour()) }
    }
    iterate(CoreScriptComponents.LUMINANCE) { entity, luminance ->
        val level = luminance.castedLuaValue.get("level").toint()
        luminanceArray.getOrSet(entity, { Luminance(level) }).let {
            it.value = level
        }
    }
}
