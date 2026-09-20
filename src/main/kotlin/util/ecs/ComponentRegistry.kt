package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.ComponentTypeProvider
import org.lain.cyberia.ecs.KClassComponentTypeProvider
import org.lain.engine.script.ScriptComponent
import kotlin.reflect.KClass

data class ComponentMeta(val savable: Boolean, val networking: Boolean)

object CommonComponentTypeProvider : ComponentTypeProvider {
    override fun componentTypeOf(component: Component): ComponentType<out Component> {
        return if (component is ScriptComponent) {
            component.type
        } else {
            ComponentTypeRegistry.componentTypeOf(component::class)
        }
    }
}

object ComponentTypeRegistry : KClassComponentTypeProvider {
    private val entriesByClassName =
        HashMap<String, Entry<out Component>>()

    private val entriesByTypeId =
        HashMap<String, Entry<out Component>>()

    private object ComponentTypes :
        ClassValue<IndexedComponentType<out Component>>() {

        override fun computeValue(
            type: Class<*>
        ): IndexedComponentType<out Component> {
            val className = type.kotlin.qualifiedName
                ?: error("Component class cannot be anonymous")

            return entriesByClassName[className]?.type
                ?: error("Component type $className is not registered")
        }
    }

    val count: Int
        get() = entriesByClassName.size

    fun get(id: String): Entry<out Component>? {
        return entriesByTypeId[id]
    }

    fun isRegistered(kClass: KClass<out Component>): Boolean {
        val className = kClass.qualifiedName ?: return false
        return entriesByClassName.containsKey(className)
    }

    inline fun <reified T : Component> registerComponent(
        isSavable: Boolean = false,
        isNetworking: Boolean = false,
        replicationClass: KClass<out Component>? = if (isSavable || isNetworking) T::class else null,
        id: String? = null
    ) {
        registerComponent(
            T::class,
            ComponentMeta(isSavable, isNetworking),
            id,
            replicationClass
        )
    }

    fun registerComponent(
        kClass: KClass<out Component>,
        meta: ComponentMeta,
        id: String? = null,
        replicationClass: KClass<out Component>? = if (meta.savable || meta.networking) kClass else null
    ) {
        registerComponent(
            kClass,
            EngineComponentType((id ?: kClass.simpleName!!).lowercase()),
            meta,
            replicationClass
        )
    }

    fun registerComponent(
        kClass: KClass<out Component>,
        type: IndexedComponentType<out Component>,
        meta: ComponentMeta,
        replicationClass: KClass<out Component>? = if (meta.savable || meta.networking) kClass else null
    ) {
        val className = kClass.qualifiedName ?: error("Component class cannot be anonymous")

        check(className !in entriesByClassName) { "Component class $className is already registered" }
        check(type.id !in entriesByTypeId) { "Component id ${type.id} is already registered" }

        val entry = Entry(
            type as IndexedComponentType<Component>,
            meta
        )
        SerializationRegistry.register(type.id, replicationClass)
        entriesByClassName[className] = entry
        entriesByTypeId[type.id] = entry
    }

    fun listEntries() = entriesByClassName.entries.toList()

    override fun <T : Component> componentTypeOf(
        kClass: KClass<T>
    ): IndexedComponentType<T> {
        @Suppress("UNCHECKED_CAST")
        return ComponentTypes.get(kClass.java) as IndexedComponentType<T>
    }

    data class Entry<T : Component>(
        val type: IndexedComponentType<T>,
        val meta: ComponentMeta
    )
}

fun getKotlinComponentTypeEntries() = ComponentTypeRegistry.listEntries().map { it.value.type to it.value.meta }
