package org.lain.engine

import org.lain.cyberia.ecs.ComponentTypeProviderContext
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.util.component.CommonComponentTypeProvider
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.registerAll
import org.lain.engine.util.component.registerComponents
import kotlin.to

fun bootstrap() {
    ComponentTypeRegistry.registerComponents()
    ComponentTypeRegistry.registerAll()
    ComponentTypeProviderContext.KCLASS = ComponentTypeRegistry
    ComponentTypeProviderContext.GENERAL = CommonComponentTypeProvider
    CoreScriptComponents.getAll() //lazy init
}

fun listKotlinComponentTypeEntries() =
    ComponentTypeRegistry.listEntries().map { it.value.type to it.value.meta } + CoreScriptComponents.getAll().map { it to it.meta }