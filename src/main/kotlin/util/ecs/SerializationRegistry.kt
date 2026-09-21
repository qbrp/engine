package org.lain.engine.util.ecs

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.lain.cyberia.ecs.Component
import kotlin.reflect.KClass

object SerializationRegistry {
    private val entriesByTypeId = HashMap<String, Entry>()

    fun get(id: String): Entry? = entriesByTypeId[id]

    fun listEntries(): List<Map.Entry<String, Entry>> = entriesByTypeId.entries.toList()

    @OptIn(InternalSerializationApi::class)
    internal fun register(
        id: String,
        replicationClass: KClass<out Component>?
    ) {
        check(id !in entriesByTypeId) { "Serialization entry for component id $id is already registered" }

        @Suppress("UNCHECKED_CAST")
        val serializer = if (replicationClass != null) {
            replicationClass.serializer() as KSerializer<Component>
        } else {
            null
        }

        entriesByTypeId[id] = Entry(serializer, replicationClass)
    }

    data class Entry(
        val serializer: KSerializer<Component>?,
        val replicationClass: KClass<out Component>?
    )
}
