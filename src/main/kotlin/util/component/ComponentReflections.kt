package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

fun ComponentTypeRegistry.registerAll() {
    val reflections = Reflections(
        ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage("org.lain.engine"))
            .setScanners(Scanners.SubTypes)
    )
    reflections.getSubTypesOf(Component::class.java).forEach {
        val kclass = it.kotlin
        if (!isRegistered(kclass)) {
            registerComponent(kclass, ComponentMeta(false, false), it.simpleName)
        }
    }
}