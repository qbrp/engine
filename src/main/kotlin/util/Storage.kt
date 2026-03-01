package org.lain.engine.util

import kotlinx.serialization.Serializable
import java.util.*

class IdCollisionException(id: Any) : RuntimeException("Object with id $id already contained")

open class Storage<K : Any, T : Any> : Iterable<T> {
    protected open val map = mutableMapOf<K, T>()

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