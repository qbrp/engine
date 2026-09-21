package org.lain.engine.data

import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import org.lain.cyberia.ecs.Component
import org.lain.engine.util.ecs.SerializationRegistry
import kotlin.reflect.KClass

fun SerializersModuleBuilder.polymorphicComponentSerializer() {
    val entries = SerializationRegistry.listEntries()
        .mapNotNull { (_, entry) ->
            val serializer = entry.serializer ?: return@mapNotNull null
            val replicationClass = entry.replicationClass ?: return@mapNotNull null
            replicationClass to serializer
        }

    polymorphic(Component::class) {
        entries.forEach { (replicationClass, serializer) ->
            @Suppress("UNCHECKED_CAST")
            subclass(replicationClass as KClass<Component>, serializer)
        }
    }
}
