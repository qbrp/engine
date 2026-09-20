package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.*
import java.util.concurrent.ConcurrentHashMap

fun ComponentState(builder: ComponentState.() -> Unit): ComponentState {
    return ComponentState().apply(builder)
}

@Suppress("UNCHECKED_CAST")
class ComponentState(components: List<Component> = emptyList()) : ComponentManager {
    private val components = ConcurrentHashMap<ComponentType<out Component>, Component>()
    val entries
        get() = components.entries

    init {
        components.forEach {
            setComponent(componentTypeOf(it) as ComponentType<Component>, it)
        }
    }

    override fun getComponents(): List<Component> {
        return components.values.toList()
    }

    override fun <T : Component> setComponent(type: ComponentType<T>, component: T): T {
        val old = components.putIfAbsent(type, component)
        if (old != null) {
            throw ComponentCollisionException("Component ${component::class} already added")
        }
        return component
    }

    override fun <T : Component> removeComponent(component: T): T? {
        return components.entries
            .firstOrNull { it.value === component }
            ?.let { (cls, _) -> removeComponent(cls) as T? }
    }

    override fun <T : Component> removeComponent(type: ComponentType<T>): T? {
        return components.remove(type) as? T?
    }

    override fun <T : Component> getComponent(type: ComponentType<T>): T? {
        return components[type] as? T
    }

    override fun copyTo(componentState: ComponentManager){
        getComponents().forEach { componentState.set(it) }
    }
}