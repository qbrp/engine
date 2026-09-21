package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.Component
import org.lain.engine.script.ScriptComponent
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

fun ComponentTypeRegistry.registerReflectedComponents() {
    val reflections = Reflections(
        ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage("org.lain.engine"))
            .setScanners(Scanners.SubTypes)
    )
    reflections.getSubTypesOf(Component::class.java)
        .asSequence()
        .filter { it != ScriptComponent::class.java }
        .filter { !it.isInterface && !it.isAnonymousClass }
        .filter { !it.name.startsWith("org.lain.engine.client.") }
        .sortedBy { it.name }
        .forEach {
            val kclass = it.kotlin
            if (!isRegistered(kclass) && it != ScriptComponent::class.java) {
                registerComponent(kclass, ComponentMeta(false, false), it.simpleName)
            }
        }
}

fun ComponentTypeRegistry.registerAllClient() {
    val reflections = Reflections(
        ConfigurationBuilder()
            .setUrls(ClasspathHelper.forPackage("org.lain.engine.client"))
            .setScanners(Scanners.SubTypes)
    )
    reflections.getSubTypesOf(Component::class.java)
        .asSequence()
        .filter { it != ScriptComponent::class.java }
        .filter { !it.isInterface && !it.isAnonymousClass }
        .sortedBy { it.name }
        .forEach {
            val kclass = it.kotlin
            if (!isRegistered(kclass) && it != ScriptComponent::class.java) {
                registerComponent(kclass, ComponentMeta(false, false), it.simpleName)
            }
        }
}
