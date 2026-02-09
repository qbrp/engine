package org.lain.engine.util

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Вспомогательный интерфейс для определения компонентов
 */
interface Component


//// Exception


class ComponentNotFoundException(message: String) : RuntimeException(message)

class ComponentCollisionException(message: String) : RuntimeException(message)


//// Manager


interface ComponentManager : Iterable<Component> {
    // Iterators

    fun getComponents(): List<Component>

    override fun iterator(): Iterator<Component> = getComponents().iterator()

    override fun spliterator(): Spliterator<Component> = getComponents().spliterator()

    // Operations

    /**
    * @throws ComponentCollisionException
     */
    fun <T : Component> setComponent(type: KClass<T>, component: T): T

    fun <T : Component> replaceComponent(type: KClass<T>, factory: () -> T): T? {
        val old = removeComponent(type)
        return if (old != null) {
            setComponent(type, factory())
        } else null
    }

    fun <T : Component> getComponentOrSet(type: KClass<T>, factory: () -> T, ): T {
        getComponent(type)?.let { return it }
        return setComponent(type, factory())
    }

    fun <T : Component> removeComponent(component: T): T?

    fun <T : Component> removeComponent(type: KClass<T>): T?

    fun removeAll() = forEach { removeComponent(it) }

    // Getters

    fun <T : Component> getComponent(clazz: KClass<T>): T?

    fun <T : Component> getComponentsOfClass(clazz: KClass<T>): List<T>
}

@Suppress("UNCHECKED_CAST")
class ComponentState(components: List<Component> = emptyList()) : ComponentManager {
    init {
        components.forEach { set(it) }
    }

    private val components = ConcurrentHashMap<KClass<out Component>, Component>()

    override fun getComponents(): List<Component> {
        return components.values.toList()
    }

    override fun <T : Component> setComponent(type: KClass<T>, component: T): T {
        val old = components.putIfAbsent(type, component)
        if (old != null) {
            throw ComponentCollisionException("Component $type already added")
        }
        return component
    }

    override fun <T : Component> removeComponent(component: T): T? {
        return components.entries
            .firstOrNull { it.value === component }
            ?.let { (cls, _) -> components.remove(cls) as? T? }
    }

    override fun <T : Component> removeComponent(type: KClass<T>): T? {
        return components.remove(type) as? T?
    }

    override fun <T : Component> getComponent(clazz: KClass<T>): T? {
        return components[clazz] as? T
    }

    override fun <T : Component> getComponentsOfClass(clazz: KClass<T>): List<T> {
        val comp = components[clazz] as? T
        return if (comp != null) listOf(comp) else emptyList()
    }
}


//// Extensions

inline fun <reified T : Component> ComponentManager.setNullable(
    component: T?,
): T? {
    return component?.let { setComponent(T::class, it) }
}

inline fun <reified T : Component> ComponentManager.set(
    component: T,
): T {
    return setComponent(T::class, component)
}

inline fun <reified T : Component> ComponentManager.getOrSet(
    noinline factory: () -> T
): T {
    return getComponentOrSet(T::class, factory)
}

inline fun <reified T : Component> ComponentManager.replaceOrSet(
    noinline factory: () -> T,
): T {
    return replaceComponent(T::class, factory) ?: setComponent(T::class, factory())
}

inline fun <reified T : Component> ComponentManager.replaceOrSet(
    component: T
): T {
    return replace(component) ?: setComponent(T::class, component)
}

inline fun <reified T : Component> ComponentManager.remove(): T? {
    return removeComponent(T::class)
}

inline fun <reified T : Component> ComponentManager.get(): T? {
    return getComponent(T::class)
}

inline fun <reified T : Component> ComponentManager.require(): T {
    return get<T>() ?: throw ComponentNotFoundException("Компонент ${T::class.simpleName} не найден")
}

inline fun <reified T : Component> ComponentManager.apply(todo: T.() -> Unit): T {
    return require<T>().apply(todo)
}

inline fun <reified T : Component> ComponentManager.handle(todo: T.() -> Unit): T? {
    return get<T>()?.apply(todo)
}

inline fun <reified T : Component, R> ComponentManager.let(todo: T.() -> R): R {
    return require<T>().let(todo)
}

inline fun <reified T : Component> ComponentManager.has(): Boolean {
    return get<T>() != null
}

inline fun <reified T : Component> ComponentManager.replace(
    noinline factory: () -> T
): T? {
    return replaceComponent(T::class, factory)
}

inline fun <reified T : Component> ComponentManager.replace(
    component: T
): T? {
    return replaceComponent(T::class) { component }
}