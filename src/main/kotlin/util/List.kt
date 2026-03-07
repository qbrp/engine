package org.lain.engine.util

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
