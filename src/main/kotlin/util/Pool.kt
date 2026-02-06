@file:Suppress("UNCHECKED_CAST")
package org.lain.engine.util

object PrimitiveArrayPool {
    private val lock = Any()
    private val floatPool = mutableMapOf<Int, MutableList<FloatArray>>()
    private val intPool = mutableMapOf<Int, MutableList<IntArray>>()
    private val booleanPool = mutableMapOf<Int, MutableList<BooleanArray>>()

    fun getFloat(size: Int): FloatArray = synchronized(lock) {
        floatPool[size]?.removeLastOrNull() ?: FloatArray(size)
    }

    fun free(array: FloatArray, fill: Float = 0f) = synchronized(lock) {
        array.fill(fill)
        floatPool.getOrPut(array.size) { mutableListOf() }.add(array)
    }

    fun getInt(size: Int): IntArray = synchronized(lock) {
        intPool[size]?.removeLastOrNull() ?: IntArray(size)
    }

    fun free(array: IntArray, fill: Int = 0) = synchronized(lock) {
        array.fill(fill)
        intPool.getOrPut(array.size) { mutableListOf() }.add(array)
    }

    fun getBool(size: Int): BooleanArray = synchronized(lock) {
        booleanPool[size]?.removeLastOrNull() ?: BooleanArray(size)
    }

    fun free(array: BooleanArray, fill: Boolean = false) = synchronized(lock) {
        array.fill(fill)
        booleanPool.getOrPut(array.size) { mutableListOf() }.add(array)
    }
}