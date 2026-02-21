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