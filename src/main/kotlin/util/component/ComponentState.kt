package org.lain.engine.util.component

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentCollisionException
import org.lain.cyberia.ecs.ComponentManager
import org.lain.cyberia.ecs.set
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

interface Entity : ComponentManager {
    val stringId: String
}

fun ComponentState(builder: ComponentState.() -> Unit): ComponentState {
    return ComponentState().apply(builder)
}

@Suppress("UNCHECKED_CAST")
class ComponentState(components: List<Component> = emptyList()) : ComponentManager {
    private val components = ConcurrentHashMap<KClass<out Component>, Component>()
    private val byName = ConcurrentHashMap<String, Component>()

    init { components.forEach { setComponent<Component>(it::class as KClass<Component>, it) } }

    override fun getComponents(): List<Component> {
        return components.values.toList()
    }

    override fun <T : Component> setComponent(type: KClass<T>, component: T): T {
        val old = components.putIfAbsent(type, component)
        if (old != null) {
            throw ComponentCollisionException("Component ${component::class} already added")
        } else {
            byName[type.componentName] = component
        }
        return component
    }

    override fun <T : Component> removeComponent(component: T): T? {
        return components.entries
            .firstOrNull { it.value === component }
            ?.let { (cls, _) -> removeComponent(cls) as T? }
    }

    override fun <T : Component> removeComponent(type: KClass<T>): T? {
        val result = components.remove(type) as? T?
        if (result != null) byName.remove(type.componentName)
        return result
    }

    override fun <T : Component> getComponent(clazzName: String): T? {
        return byName[clazzName] as? T
    }

    override fun <T : Component> getComponent(clazz: KClass<T>): T? {
        return components[clazz] as? T
    }

    override fun copyTo(componentState: ComponentManager){
        getComponents().forEach { componentState.set(it) }
    }

    private val KClass<out Component>.componentName
        get() = (simpleName ?: jvmName).lowercase()
}