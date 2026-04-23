package org.lain.engine.storage

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.util.component.ComponentTypeRegistry
import java.util.*
import kotlin.reflect.KClass

class ComponentSerializerNotRegisteredException(componentClass: KClass<out Component>) : Exception("Serializer not registered for component ${componentClass.simpleName}")

@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun PolymorphicModuleBuilder<Component>.componentSubclassSerializers() {
    val classes = ComponentTypeRegistry.listEntries()
        .filter { (_, entry) -> entry.meta.savable || entry.meta.networking }
        .map { (_, entry) -> entry.meta.serializationClass }
    val exceptions = mutableListOf<ComponentSerializerNotRegisteredException>()
    classes.forEach {
        val serializer = runCatching { it.serializer() }
            .onFailure {
                exception -> if (exception is SerializationException) {
                    exceptions += ComponentSerializerNotRegisteredException(it)
                }
            }
            .getOrNull() as? KSerializer<Component>
        subclass(it as KClass<Component>, serializer ?: return@forEach)
    }
    if (exceptions.isNotEmpty()) {
        exceptions.forEach { LOGGER.error(it.message) }
        error("Serializer not registered for $exceptions components")
    }
}

val COMPONENT_SERIALIZERS_MODULE = SerializersModule {
    polymorphic(Component::class) { componentSubclassSerializers() }
}

@OptIn(ExperimentalSerializationApi::class)
val ENTITY_CBOR = Cbor {
    ignoreUnknownKeys = true
    serializersModule = COMPONENT_SERIALIZERS_MODULE
}

@Serializable
data class PersistentId(val value: String) : Component {
    override fun toString(): String = value
    companion object {
        fun next() = PersistentId(UUID.randomUUID().toString())
    }
}

@OptIn(ExperimentalSerializationApi::class)
fun serializeEntityComponents(components: List<Component>): ByteArray {
    return ENTITY_CBOR.encodeToByteArray(ListSerializer(serializer<Component>()), components)
}

@OptIn(ExperimentalSerializationApi::class)
fun deserializeEntityComponents(array: ByteArray): List<Component> {
    return ENTITY_CBOR.decodeFromByteArray(ListSerializer(serializer<Component>()), array)
}

fun WriteComponentAccess.instantiateEntity(id: PersistentId, components: List<Component>): EntityId {
    return addEntity {
        setComponent(id)
        components.forEach { setComponent(it) }
    }
}
