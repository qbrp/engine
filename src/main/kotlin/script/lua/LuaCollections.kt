package org.lain.engine.script.lua

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import java.util.Queue

class LuaMutableMap<K : Any, V : Any>(
    private val values: MutableMap<K, V>,
    private val keyToLua: (K) -> LuaValue,
    private val keyFromLua: (LuaValue) -> K,
    private val valueToLua: (V) -> LuaValue,
    private val valueFromLua: (LuaValue) -> V,
    private val onChanged: () -> Unit,
) {
    val size: Int
        get() = values.size

    fun get(key: LuaValue): LuaValue =
        values[keyFromLua(key)]?.let(valueToLua) ?: LuaValue.NIL

    fun containsKey(key: LuaValue): Boolean = values.containsKey(keyFromLua(key))

    fun set(key: LuaValue, value: LuaValue) {
        val decodedKey = keyFromLua(key)
        if (value.isnil()) {
            if (values.remove(decodedKey) != null) {
                onChanged()
            }
        } else {
            values[decodedKey] = valueFromLua(value)
            onChanged()
        }
    }

    fun remove(key: LuaValue): LuaValue {
        val removed = values.remove(keyFromLua(key)) ?: return LuaValue.NIL
        onChanged()
        return valueToLua(removed)
    }

    fun clear() {
        if (values.isEmpty()) return
        values.clear()
        onChanged()
    }

    fun toTable(): LuaTable = LuaTable().also { table ->
        values.forEach { (key, value) ->
            table[keyToLua(key)] = valueToLua(value)
        }
    }

    fun toLuaValue(): LuaUserdata = LUA_MUTABLE_MAP_TYPE.newInstance(this)
}

class LuaList<T>(
    private val values: List<T>,
    private val valueToLua: (T) -> LuaValue,
) {
    val size: Int
        get() = values.size

    fun get(index: LuaValue): LuaValue {
        val kotlinIndex = index.luaListIndexOrNull() ?: return LuaValue.NIL
        return values.getOrNull(kotlinIndex)?.let(valueToLua) ?: LuaValue.NIL
    }

    fun toTable(): LuaTable = values.toLuaList(valueToLua)

    fun toLuaValue(): LuaUserdata = LUA_LIST_TYPE.newInstance(this)
}

class LuaMutableList<T>(
    private val values: MutableList<T>,
    private val valueToLua: (T) -> LuaValue,
    private val valueFromLua: (LuaValue) -> T,
    private val onChanged: () -> Unit,
) {
    val size: Int
        get() = values.size

    fun get(index: LuaValue): LuaValue {
        val kotlinIndex = index.luaListIndexOrNull() ?: return LuaValue.NIL
        return values.getOrNull(kotlinIndex)?.let(valueToLua) ?: LuaValue.NIL
    }

    fun set(index: LuaValue, value: LuaValue) {
        val kotlinIndex = index.requireLuaListIndex()
        when {
            value.isnil() && kotlinIndex in values.indices -> {
                values.removeAt(kotlinIndex)
                onChanged()
            }

            kotlinIndex in values.indices -> {
                values[kotlinIndex] = valueFromLua(value)
                onChanged()
            }

            kotlinIndex == values.size && !value.isnil() -> {
                values.add(valueFromLua(value))
                onChanged()
            }

            else -> error("List index ${kotlinIndex + 1} is outside list bounds")
        }
    }

    fun add(value: LuaValue) {
        values.add(valueFromLua(value))
        onChanged()
    }

    fun removeAt(index: LuaValue): LuaValue {
        val kotlinIndex = index.requireLuaListIndex()
        require(kotlinIndex in values.indices) {
            "List index ${kotlinIndex + 1} is outside list bounds"
        }
        val removed = values.removeAt(kotlinIndex)
        onChanged()
        return valueToLua(removed)
    }

    fun clear() {
        if (values.isEmpty()) return
        values.clear()
        onChanged()
    }

    fun toTable(): LuaTable = values.toLuaList(valueToLua)

    fun toLuaValue(): LuaUserdata = LUA_MUTABLE_LIST_TYPE.newInstance(this)
}

class LuaQueue<T : Any>(
    private val values: Queue<T>,
    private val valueToLua: (T) -> LuaValue,
    private val valueFromLua: (LuaValue) -> T,
    private val onChanged: () -> Unit,
) {
    val size: Int
        get() = values.size

    fun get(index: LuaValue): LuaValue {
        val kotlinIndex = index.luaListIndexOrNull() ?: return LuaValue.NIL
        return values.elementAtOrNull(kotlinIndex)?.let(valueToLua) ?: LuaValue.NIL
    }

    fun peek(): LuaValue = values.peek()?.let(valueToLua) ?: LuaValue.NIL

    fun poll(): LuaValue {
        val value = values.poll() ?: return LuaValue.NIL
        onChanged()
        return valueToLua(value)
    }

    fun add(value: LuaValue) {
        values.add(valueFromLua(value))
        onChanged()
    }

    fun clear() {
        if (values.isEmpty()) return
        values.clear()
        onChanged()
    }

    fun toTable(): LuaTable = values.toLuaList(valueToLua)

    fun toLuaValue(): LuaUserdata = LUA_QUEUE_TYPE.newInstance(this)
}

private val LUA_MUTABLE_MAP_TYPE = LuaUserdataType<LuaMutableMap<*, *>> {
    indexSelf { self, key ->
        when (key.tojstring()) {
            "size" -> luaValue(self.size)
            "is_empty" -> luaValue(self.size == 0)
            else -> self.get(key)
        }
    }
    newIndexSelf(LuaMutableMap<*, *>::set)
    functionSelf2("get", LuaMutableMap<*, *>::get)
    functionSelf2("contains_key") { self, key -> luaValue(self.containsKey(key)) }
    functionSelf2("remove", LuaMutableMap<*, *>::remove)
    functionSelf("clear") { self -> self.clear(); LuaValue.NIL }
    functionSelf("to_table", LuaMutableMap<*, *>::toTable)
    lengthSelf(LuaMutableMap<*, *>::size)
}

private val LUA_LIST_TYPE = LuaUserdataType<LuaList<*>> {
    indexSelf { self, key ->
        when (key.tojstring()) {
            "size" -> luaValue(self.size)
            "is_empty" -> luaValue(self.size == 0)
            else -> self.get(key)
        }
    }
    functionSelf2("get", LuaList<*>::get)
    functionSelf("to_table", LuaList<*>::toTable)
    lengthSelf(LuaList<*>::size)
}

private val LUA_MUTABLE_LIST_TYPE = LuaUserdataType<LuaMutableList<*>> {
    indexSelf { self, key ->
        when (key.tojstring()) {
            "size" -> luaValue(self.size)
            "is_empty" -> luaValue(self.size == 0)
            else -> self.get(key)
        }
    }
    newIndexSelf(LuaMutableList<*>::set)
    functionSelf2("get", LuaMutableList<*>::get)
    functionSelf3("set") { self, index, value -> self.set(index, value); LuaValue.NIL }
    functionSelf2("add") { self, value -> self.add(value); LuaValue.NIL }
    functionSelf2("remove_at", LuaMutableList<*>::removeAt)
    functionSelf("clear") { self -> self.clear(); LuaValue.NIL }
    functionSelf("to_table", LuaMutableList<*>::toTable)
    lengthSelf(LuaMutableList<*>::size)
}

private val LUA_QUEUE_TYPE = LuaUserdataType<LuaQueue<*>> {
    indexSelf { self, key ->
        when (key.tojstring()) {
            "size" -> luaValue(self.size)
            "is_empty" -> luaValue(self.size == 0)
            else -> self.get(key)
        }
    }
    functionSelf("peek", LuaQueue<*>::peek)
    functionSelf("poll", LuaQueue<*>::poll)
    functionSelf2("add") { self, value -> self.add(value); LuaValue.NIL }
    functionSelf("clear") { self -> self.clear(); LuaValue.NIL }
    functionSelf("to_table", LuaQueue<*>::toTable)
    lengthSelf(LuaQueue<*>::size)
}

fun <K : Any, V : Any> MutableMap<K, V>.toLuaMutableMapUserdata(
    keyToLua: (K) -> LuaValue,
    keyFromLua: (LuaValue) -> K,
    valueToLua: (V) -> LuaValue,
    valueFromLua: (LuaValue) -> V,
    onChanged: () -> Unit = {},
): LuaUserdata = LuaMutableMap(
    this,
    keyToLua,
    keyFromLua,
    valueToLua,
    valueFromLua,
    onChanged,
).toLuaValue()

fun <T> List<T>.toLuaListUserdata(
    valueToLua: (T) -> LuaValue,
): LuaUserdata = LuaList(this, valueToLua).toLuaValue()

fun <T> MutableList<T>.toLuaMutableListUserdata(
    valueToLua: (T) -> LuaValue,
    valueFromLua: (LuaValue) -> T,
    onChanged: () -> Unit = {},
): LuaUserdata = LuaMutableList(
    this,
    valueToLua,
    valueFromLua,
    onChanged,
).toLuaValue()

fun <T : Any> Queue<T>.toLuaQueueUserdata(
    valueToLua: (T) -> LuaValue,
    valueFromLua: (LuaValue) -> T,
    onChanged: () -> Unit = {},
): LuaUserdata = LuaQueue(
    this,
    valueToLua,
    valueFromLua,
    onChanged,
).toLuaValue()

private fun LuaValue.luaListIndexOrNull(): Int? {
    if (!isint()) return null
    return toint().minus(1).takeIf { it >= 0 }
}

private fun LuaValue.requireLuaListIndex(): Int {
    return luaListIndexOrNull() ?: error("List index must be a positive integer")
}
