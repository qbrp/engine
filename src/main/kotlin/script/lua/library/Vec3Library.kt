package org.lain.engine.script.lua.library

import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaUserdataTable
import org.lain.engine.script.lua.luaValue
import org.lain.engine.util.math.MutableEVec3
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL

class Vec3Library {
    private val functions = luaUserdataTable<MutableEVec3> {
        functionSelf("copy") { vector ->
            coerceToLua(MutableEVec3(vector))
        }
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
        functionSelf("normalized") { vector -> coerceToLua(MutableEVec3(vector.normalize())) }
    }

    private val operatorFunctions = luaTable {
        index { self, key ->
            when (key.tojstring()) {
                "x" -> luaValue(self.asEngineVec3().x.toDouble())
                "y" -> luaValue(self.asEngineVec3().y.toDouble())
                "z" -> luaValue(self.asEngineVec3().z.toDouble())
                else -> functions[key]
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
            coerceToLua(
                MutableEVec3(left.asEngineVec3()).also {
                    it.mutateAdd(source.x, source.y, source.z)
                }
            )
        }
        function2("__sub") { left, right ->
            coerceToLua(
                MutableEVec3(left.asEngineVec3()).also {
                    it.mutateSub(right.asEngineVec3())
                }
            )
        }
        function1("__unm") { value ->
            coerceToLua(
                MutableEVec3(value.asEngineVec3()).also {
                    it.x = -it.x
                    it.y = -it.y
                    it.z = -it.z
                }
            )
        }
        function2("__mul") { left, right ->
            val (vector, scalar) = if (left.isnumber()) {
                right.asEngineVec3() to left.checknumber().tofloat()
            } else {
                left.asEngineVec3() to right.checknumber().tofloat()
            }
            coerceToLua(
                MutableEVec3(vector).also {
                    it.x *= scalar
                    it.y *= scalar
                    it.z *= scalar
                }
            )
        }
        function2("__div") { value, scalar ->
            val divisor = scalar.checknumber().tofloat()
            coerceToLua(
                MutableEVec3(value.asEngineVec3()).also {
                    it.mutateDiv(divisor, divisor, divisor)
                }
            )
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

    val library = luaTable {
        "operator_functions"(operatorFunctions)
        "functions"(functions)
        function3("new") { x, y, z ->
            coerceToLua(
                MutableEVec3(
                    x.optdouble(0.0).toFloat(),
                    y.optdouble(0.0).toFloat(),
                    z.optdouble(0.0).toFloat()
                )
            )
        }
    }

    fun coerceToLua(vec: MutableEVec3): LuaUserdata {
        return LuaUserdata(vec)
            .also {
                it.setmetatable(operatorFunctions)
            }
    }
}

fun LuaValue.asEngineVec3() = checkuserdata() as MutableEVec3

context(lua: LuaScriptEngine)
fun MutableEVec3.coerceToLua(): LuaValue = lua.vec3Library.coerceToLua(this)
