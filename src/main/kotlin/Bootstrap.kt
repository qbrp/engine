package org.lain.engine

import org.lain.cyberia.ecs.ComponentTypeProviderContext
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.util.component.CommonComponentTypeProvider
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.registerAll
import org.lain.engine.util.component.registerComponents

fun bootstrap() {
    ComponentTypeRegistry.registerComponents()
    ComponentTypeRegistry.registerAll()
    ComponentTypeProviderContext.KCLASS = ComponentTypeRegistry
    ComponentTypeProviderContext.GENERAL = CommonComponentTypeProvider
    CoreScriptComponents.getAll() //lazy init
}