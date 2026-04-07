package org.lain.engine.util.component

import org.lain.cyberia.ecs.*
import java.util.concurrent.ConcurrentHashMap

interface Entity : ComponentManager {
    val stringId: String
}

fun ComponentState(builder: ComponentState.() -> Unit): ComponentState {
    return ComponentState().apply(builder)
}

@Suppress("UNCHECKED_CAST")
class ComponentState(components: List<Component> = emptyList()) : ComponentManager {
    private val components = ConcurrentHashMap<ComponentType<out Component>, Component>()
    private val byName = ConcurrentHashMap<String, Component>()

    init { components.forEach { setComponent(componentTypeOf(it::class) as ComponentType<Component>, it) } }

    override fun getComponents(): List<Component> {
        return components.values.toList()
    }

    override fun <T : Component> setComponent(type: ComponentType<T>, component: T): T {
        val old = components.putIfAbsent(type, component)
        if (old != null) {
            throw ComponentCollisionException("Component ${component::class} already added")
        } else {
            byName[type.id] = component
        }
        return component
    }

    override fun <T : Component> removeComponent(component: T): T? {
        return components.entries
            .firstOrNull { it.value === component }
            ?.let { (cls, _) -> removeComponent(cls) as T? }
    }

    override fun <T : Component> removeComponent(type: ComponentType<T>): T? {
        val result = components.remove(type) as? T?
        if (result != null) {
            byName.remove(type.id)
        }
        return result
    }

    override fun <T : Component> getComponent(clazzName: String): T? {
        return byName[clazzName] as? T
    }

    override fun <T : Component> getComponent(type: ComponentType<T>): T? {
        return components[type] as? T
    }

    override fun copyTo(componentState: ComponentManager){
        getComponents().forEach { componentState.set(it) }
    }
}