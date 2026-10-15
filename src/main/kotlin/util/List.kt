package org.lain.engine.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.util.Queue
import kotlin.collections.ArrayDeque

fun <T : Any> Queue<T>.flush(todo: (T) -> Unit) {
    while (isNotEmpty()) {
        todo(poll())
    }
}

class FixedSizeList<T>(private val maxSize: Int, private val list: ArrayDeque<T> = ArrayDeque()) : List<T> by list {
    fun add(item: T) {
        if (list.size >= maxSize) {
            list.removeFirst()
        }
        list.addLast(item)
    }
}

typealias Predicate = () -> Boolean

fun <T> Predicate.then(factory: () -> T): T? {
    return if (this()) factory() else null
}

fun <T, C> Collection<T>.forEachWithContext(contextReceiver: (T) -> C, block: C.(T) -> Unit) {
    forEach {
        with (contextReceiver(it)) { block(it)  }
    }
}

fun <T> Collection<T>.forEachWithSelfContext(block: T.(T) -> Unit) {
    forEach { with (it) { block(it)  } }
}

fun <T : Any> MutableCollection<T>.addIfNotNull(component: T?) {
    if (component != null) this += component
}

fun <K, V> Map<K, V>.deepCopy(
    copyKey: (K) -> K,
    copyValue: (V) -> V
): Map<K, V> {
    val result = mutableMapOf<K, V>()
    for ((k, v) in this) {
        result[copyKey(k)] = copyValue(v)
    }
    return result
}

fun <T> MutableCollection<T>.replaceWith(elements: Collection<T>) {
    clear()
    addAll(elements)
}