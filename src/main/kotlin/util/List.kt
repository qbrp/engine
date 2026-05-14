package org.lain.engine.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.lain.engine.storage.ItemData
import java.util.Queue
import kotlin.collections.ArrayDeque

fun <T : Any> Queue<T>.flush(todo: (T) -> Unit) {
    while (isNotEmpty()) {
        todo(poll())
    }
}

fun <T : Any, R : Any> Queue<T>.flushMap(todo: (T) -> R): List<R> {
    val output = mutableListOf<R>()
    while (isNotEmpty()) {
        output += todo(poll())
    }
    return output
}

suspend fun <T> List<T>.mapAsyncTasks(statement: suspend (T) -> Unit) = withContext(Dispatchers.IO) {
    map {
        async {
            statement(it)
        }
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

fun <T : Any> List<T>.alsoForEach(block: (T) -> Unit): List<T> {
    forEach { block(it) }
    return this
}

fun <T : Any> List<T>.findIndexed(block: (Int, T) -> Boolean): T? {
    var index = 0
    for (elem in this) {
        if (block(index++, elem)) {
            return elem
        }
    }
    return null
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

fun <T : ItemData> MutableList<ItemData>.addIf(statement: () -> Boolean, component: () -> T) {
    if (statement()) this += component()
}

fun <T : Any> MutableCollection<T>.addIfNotNull(component: T?) {
    if (component != null) this += component
}

fun <T : Any> MutableCollection<T>.addIfNotNull(component: () -> T?) {
    addIfNotNull(component())
}
