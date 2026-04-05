package org.lain.engine

import org.lain.cyberia.ecs.ComponentTypeProviderContext
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.registerComponents

fun bootstrap() {
    ComponentTypeRegistry.registerComponents()
    ComponentTypeProviderContext.PROVIDER = ComponentTypeRegistry
}