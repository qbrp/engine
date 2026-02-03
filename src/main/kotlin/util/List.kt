package org.lain.engine.util

import java.util.Queue

fun <T : Any> Queue<T>.flush(todo: (T) -> Unit) {
    while (isNotEmpty()) {
        todo(poll())
    }
}

class FixedSizeList<T>(private val maxSize: Int) {
    private val list = ArrayDeque<T>()

    fun add(item: T) {
        if (list.size >= maxSize) {
            list.removeFirst()
        }
        list.addLast(item)
    }

    fun contains(item: T) = list.contains(item)

    fun toList(): List<T> = list.toList()
}

fun <T : Any> List<T>.alsoForEach(block: (T) -> Unit): List<T> {
    forEach { block(it) }
    return this
}