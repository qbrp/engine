@file:Suppress("UNCHECKED_CAST")
package org.lain.engine.util

object PrimitiveArrayPool {
    private val lock = Any()
    private val floatPool = mutableMapOf<Int, MutableList<FloatArray>>()
    private val intPool = mutableMapOf<Int, MutableList<IntArray>>()
    private val bytePool = mutableMapOf<Int, MutableList<ByteArray>>()

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

    fun getByte(size: Int): ByteArray = synchronized(lock) {
        bytePool[size]?.removeLastOrNull() ?: ByteArray(size)
    }

    fun free(array: ByteArray, fill: Byte = 0) = synchronized(lock) {
        array.fill(fill)
        bytePool.getOrPut(array.size) { mutableListOf() }.add(array)
    }
}