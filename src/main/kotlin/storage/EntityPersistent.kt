package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.serializer
import org.lain.engine.container.*
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.component.setComponent
import org.lain.engine.world.World
import java.util.*

fun PolymorphicModuleBuilder<Component>.polymorphicComponent() {
    subclass(PersistentId::class)
    subclass(ContainedIn::class)
    subclass(Slots::class)
    subclass(OccupiedSlots::class)
    subclass(AssignedSlot::class)
    subclass(AssignItem::class)
}

val COMPONENT_SERIALIZERS_MODULE = SerializersModule {
    polymorphic(Component::class) { polymorphicComponent() }
}

@OptIn(ExperimentalSerializationApi::class)
val ENTITY_CBOR = Cbor {
    ignoreUnknownKeys = true
    serializersModule = COMPONENT_SERIALIZERS_MODULE
}

@Serializable
data class PersistentId(val uuid: String) : Component {
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

fun World.instantiateEntity(id: PersistentId, components: List<Component>): EntityId {
    return componentManager.addEntity {
        setComponent(id)
        components.forEach { setComponent(it) }
    }
}