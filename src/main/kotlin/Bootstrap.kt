package org.lain.engine

import org.lain.cyberia.ecs.ComponentTypeProviderContext
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.lua.library.LuaComponentBindingRegistry
import org.lain.engine.util.ecs.CommonComponentTypeProvider
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.lain.engine.util.ecs.getKotlinComponentTypeEntries
import org.lain.engine.util.ecs.registerKotlinComponents
import kotlin.to

private val bootstrapLock = Any()

@Volatile
private var bootstrapped = false

fun bootstrap() {
    if (bootstrapped) return

    synchronized(bootstrapLock) {
        if (bootstrapped) return

        ComponentTypeRegistry.registerKotlinComponents()
        ComponentTypeProviderContext.PROVIDER = ComponentTypeRegistry
        ComponentTypeProviderContext.GENERAL = CommonComponentTypeProvider
        CoreScriptComponents.getAll() //lazy init
        LuaComponentBindingRegistry.initializeCacheComponentTypes()
        bootstrapped = true
    }
}

fun listKotlinComponentTypeEntries() =
    getKotlinComponentTypeEntries() + CoreScriptComponents.getAll().map { it to it.meta }
