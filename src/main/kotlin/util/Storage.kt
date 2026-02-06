package org.lain.engine.util

import kotlinx.serialization.Serializable
import java.util.Spliterator

class IdCollisionException(id: Any) : RuntimeException("Object with id $id already contained")

open class Storage<K : Any, T : Any> : Iterable<T> {
    private val map = mutableMapOf<K, T>()

    fun get(key: K): T? {
        return map[key]
    }

    fun add(key: K, obj: T): T {
        if (map.contains(key)) throw IdCollisionException(key)
        map[key] = obj
        return obj
    }

    fun remove(key: K): T? = map.remove(key)

    fun getAll(): List<T> = map.values.toList()

    fun putIfAbsent(key: K, lazyObj: () -> T): T? {
        return map[key] ?: add(key, lazyObj())
    }

    fun clear() {
        map.clear()
    }

    override fun iterator(): Iterator<T> = getAll().iterator()
    override fun spliterator(): Spliterator<T> = getAll().spliterator()
}

@JvmInline
@Serializable
value class NamespaceId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

data class Namespace<K, V>(
    val id: NamespaceId,
    val entries: Map<K, V>
)

class NamespacedStorage<K, V> {
    var entries: Map<K, V> = mapOf()
        private set
    var namespaces: Map<NamespaceId, Namespace<K, V>> = mapOf()
        private set
    var identifiers: List<String> = listOf()
        private set

    fun upload(namespaces: List<Namespace<K, V>>) {
        val identifiers2 = mutableListOf<String>()
        val entries2 = mutableMapOf<K, V>()
        val namespaces2 = mutableMapOf<NamespaceId, Namespace<K, V>>()
        for (namespace in namespaces) {
            namespaces2[namespace.id] = namespace
            entries2 += namespace.entries
            identifiers2 += (namespace.entries.keys.map { it.toString() } + namespace.id.value)
        }
        this.identifiers = identifiers2
        this.namespaces = namespaces2
        this.entries = entries2
    }

    operator fun get(index: K): V {
        return entries[index] ?: error("Element $index not found")
    }
}
