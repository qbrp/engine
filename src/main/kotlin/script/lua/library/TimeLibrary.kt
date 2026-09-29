package org.lain.engine.script.lua.library

import kotlinx.serialization.Serializable
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.NIL
import org.lain.engine.script.lua.asUserdataOrThrow
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.nullable
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

context(lua: LuaScriptEngine)
fun InstantUserdataType() = LuaUserdataType<Instant> {
    functionSelf2("zoned") { self, zoneL ->
        val zone = zoneL.asUserdataOrThrow<ZoneId>()
        lua.zonedDateTimeUserdataType.newInstance(self.atZone(zone))
    }

    indexSelf { _, _ -> NIL }
}

fun ZoneUserdataType() = LuaUserdataType<ZoneId>() {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "id" -> self.id.luaStr()
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun ZonedDateTimeUserdataType() = LuaUserdataType<ZonedDateTime>() {
    indexSelf { self, key ->
        when (key.tojstring()) {
            "zone" -> self.zone.id.luaStr()
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun TimeLibraryTable() = luaTable {
    "instant"(lua.instantUserdataType)
    "zoned_date_time"(lua.zonedDateTimeUserdataType)
    "system_zone"(lua.zoneUserdataType.newInstance(ZoneId.systemDefault()))

    function1("zone") { idL ->
        lua.zoneUserdataType.newInstance(ZoneId.of(idL.tojstring()))
    }

    function("now") {
        Instant.now().toLuaInstant()
    }

    function3("format") { zonedDateTimeL, patternL, localeL ->
        val zoneDateTime = zonedDateTimeL.checkuserdata(ZonedDateTime::class.java) as ZonedDateTime
        val formatter = DateTimeFormatter.ofPattern(
            patternL.tojstring(),
            localeL.nullable()?.tojstring()?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        )
        zoneDateTime.format(formatter)
            .luaStr()
    }
}

context(lua: LuaScriptEngine)
fun Instant.toLuaInstant(): LuaValue {
    return lua.instantUserdataType.newInstance(this)
}