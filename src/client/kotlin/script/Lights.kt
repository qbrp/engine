package org.lain.engine.client.script

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.render.LightBehaviour
import org.lain.engine.client.render.LightSource
import org.lain.engine.client.render.Luminance
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.luaTable
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable

fun LuaTable.toLightBehaviour(): LightBehaviour {
    val parameters = get("params").checktable()
    return when(val type = get("type").tojstring()) {
        "sphere" -> LightBehaviour.Sphere(parameters.get("radius").toint())
        else -> error("Unsupported light behaviour type: $type")
    }
}

fun World.updateScriptLightSystem() {
    val luminanceArray = componentManager.getComponentArray<Luminance>()
    val lightSourceArray = componentManager.getComponentArray<LightSource>()
    iterate(CoreScriptComponents.LIGHT_SOURCE) { entity, lightSource ->
        val behaivour = lightSource.luaTable.get("behaviour").checktable()
        lightSourceArray.getOrSet(entity) { LightSource(behaivour.toLightBehaviour()) }
    }
    iterate(CoreScriptComponents.LUMINANCE) { entity, luminance ->
        val level = luminance.luaTable.get("level").toint()
        luminanceArray.getOrSet(entity, { Luminance(level) }).let {
            it.value = level
        }
    }
}