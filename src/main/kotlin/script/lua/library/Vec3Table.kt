package org.lain.engine.script.lua.library

import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.util.math.MutableEVec3
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

fun LuaValue.asEngineVec3() = checkuserdata() as MutableEVec3

fun MutableEVec3.coerceToLua(): LuaUserdata = LuaUserdata(this).also {
    it.setmetatable(vec3MetaTable)
}

private val vec3Functions = luaUserdataTable<MutableEVec3> {
    functionSelf("copy") { vector -> MutableEVec3(vector).coerceToLua() }
    functionSelf2("set") { vector, other ->
        vector.set(other.asEngineVec3())
        NIL
    }
    functionSelf2("add") { vector, other ->
        val source = other.asEngineVec3()
        vector.mutateAdd(source.x, source.y, source.z)
        NIL
    }
    functionSelf2("sub") { vector, other ->
        vector.mutateSub(other.asEngineVec3())
        NIL
    }
    functionSelf2("mul") { vector, scalar ->
        val value = scalar.checknumber().tofloat()
        vector.x *= value
        vector.y *= value
        vector.z *= value
        NIL
    }
    functionSelf2("div") { vector, scalar ->
        val value = scalar.checknumber().tofloat()
        vector.mutateDiv(value, value, value)
        NIL
    }
    functionSelf("normalize") { vector ->
        vector.set(vector.normalize())
        NIL
    }
    functionSelf2("dot") { vector, other ->
        val source = other.asEngineVec3()
        luaValue((vector.x * source.x + vector.y * source.y + vector.z * source.z).toDouble())
    }
    functionSelf("length_squared") { vector ->
        luaValue((vector.x * vector.x + vector.y * vector.y + vector.z * vector.z).toDouble())
    }
    functionSelf("length") { vector -> luaValue(vector.length().toDouble()) }
    functionSelf("normalized") { vector -> MutableEVec3(vector.normalize()).coerceToLua() }
}

internal val vec3MetaTable = luaTable {
    index { self, key ->
        when (key.tojstring()) {
            "x" -> luaValue(self.asEngineVec3().x.toDouble())
            "y" -> luaValue(self.asEngineVec3().y.toDouble())
            "z" -> luaValue(self.asEngineVec3().z.toDouble())
            else -> vec3Functions[key]
        }
    }
    newIndex { self, key, value ->
        when (key.tojstring()) {
            "x" -> self.asEngineVec3().x = value.checknumber().tofloat()
            "y" -> self.asEngineVec3().y = value.checknumber().tofloat()
            "z" -> self.asEngineVec3().z = value.checknumber().tofloat()
            else -> error("vec3 has no writable field '${key.tojstring()}'")
        }
    }
    function2("__add") { left, right ->
        val source = right.asEngineVec3()
        MutableEVec3(left.asEngineVec3()).also {
            it.mutateAdd(source.x, source.y, source.z)
        }.coerceToLua()
    }
    function2("__sub") { left, right ->
        MutableEVec3(left.asEngineVec3()).also {
            it.mutateSub(right.asEngineVec3())
        }.coerceToLua()
    }
    function1("__unm") { value ->
        MutableEVec3(value.asEngineVec3()).also {
            it.x = -it.x
            it.y = -it.y
            it.z = -it.z
        }.coerceToLua()
    }
    function2("__mul") { left, right ->
        val (vector, scalar) = if (left.isnumber()) {
            right.asEngineVec3() to left.checknumber().tofloat()
        } else {
            left.asEngineVec3() to right.checknumber().tofloat()
        }
        MutableEVec3(vector).also {
            it.x *= scalar
            it.y *= scalar
            it.z *= scalar
        }.coerceToLua()
    }
    function2("__div") { value, scalar ->
        val divisor = scalar.checknumber().tofloat()
        MutableEVec3(value.asEngineVec3()).also {
            it.mutateDiv(divisor, divisor, divisor)
        }.coerceToLua()
    }
    function2("__eq") { left, right ->
        val a = left.asEngineVec3()
        val b = right.asEngineVec3()
        luaValue(a.x == b.x && a.y == b.y && a.z == b.z)
    }
    tostring { value ->
        val vector = value.asEngineVec3()
        "vec3(${vector.x}, ${vector.y}, ${vector.z})"
    }
}

fun Vec3Table(): LuaTable = luaTable {
    function3("new") { x, y, z ->
        MutableEVec3(x.optdouble(0.0).toFloat(), y.optdouble(0.0).toFloat(), z.optdouble(0.0).toFloat())
            .coerceToLua()
    }
}
