package org.lain.engine.script.lua

import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.*
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.invokeCommand
import org.lain.engine.world.world
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction

fun LuaValue.coerceToEnginePlayer() = this.checkuserdata() as EnginePlayer

context(context: LuaContext)
fun EnginePlayer.coerceToLua(): LuaUserdata {
    val userdata = LuaUserdata(this)
    val world = world
    val worldLua = world.getLuaValue()
    val entity = with(world) { entityId.coerceToLua() }
    val entityIdLua = entityId.toLuaValue()
    val meta = object : LuaTable() {
        init {
            set("__index", object : TwoArgFunction() {
                override fun call(self: LuaValue, key: LuaValue): LuaValue {
                    return when (key.tojstring()) {
                        "id" -> luaValue(this@coerceToLua.id.toString())
                        "entity_id" -> luaValue(this@coerceToLua.entityId)
                        "entity" -> entity
                        "world" -> worldLua
                        "is_spectating" -> isSpectating.toLuaValue()
                        "is_game_master" -> isInGameMasterMode.toLuaValue()
                        else -> context.playerTable.get(key)
                    }
                }
            })
        }
    }

    userdata.setmetatable(meta)
    return userdata
}

context(ctx: LuaContext)
fun Globals.setupPlayer() {
    ctx.playerTable.set("has_permission", twoArgFunction { self, permission ->
        val player = self.coerceToEnginePlayer()
        player.hasPermission(permission.tojstring()).toLuaValue()
    })

    ctx.playerTable.set("set_flying_speed", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val speed = arg2.tofloat()
            player.setFlyingSpeed(speed)
            return NIL
        }
    })

    ctx.playerTable.set("_set_custom_max_speed", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val speed = arg2.tofloat()
            player.setCustomMaxSpeed(speed)
            return NIL
        }
    })

    ctx.playerTable.set("_reset_custom_max_speed", object : OneArgFunction() {
        override fun call(self: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            player.resetCustomMaxSpeed()
            return NIL
        }
    })

    ctx.playerTable.set("_narration", object : TwoArgFunction() {
        override fun call(self: LuaValue, arg2: LuaValue): LuaValue? {
            val player = self.coerceToEnginePlayer()
            val narration = arg2.checktable()
            player.serverNarration(
                narration.get("message").tojstring(),
                narration.get("time").toint(),
                narration.get("kick").nullable()?.toboolean() ?: false,
            )
            return NIL
        }
    })

    ctx.playerTable.set("invoke_command", threeArgFunction { self, command, root ->
        val player = self.coerceToEnginePlayer()
        val commandStr = command.tojstring()
        val rootBl = root.nullable()?.toboolean() ?: false
        player.invokeCommand(commandStr, rootBl)
        LuaValue.NIL
    })
}

context(world: World, luaContext: LuaContext)
fun EnginePlayer.prepareLuaScriptComponents() {
    entityId.setScriptComponent(
        luaTableOf(luaValue("object"), coerceToLua()),
        CoreScriptComponents.PLAYER
    )
    entityId.setScriptComponent(
        luaTableOf(luaValue("vector"), LuaValue.NIL),
        CoreScriptComponents.LOCATION
    )
}

fun World.updatePlayerScriptSystem() {
    val locationArray = componentManager.getComponentArray(CoreScriptComponents.LOCATION)
    iterate(CoreScriptComponents.LOCATION) { entity, location ->
        if (!entity.hasComponent<Location>()) {
            val array = location.luaValue.get("vector")
            entity.setComponent(Location(this@updatePlayerScriptSystem, array.toVector3f()))
        }
    }
    iterate<Location>() { entity, location ->
        val scriptLocation = locationArray.componentOf(entity) ?: return@iterate
        val table = scriptLocation.luaValue
        val vector = table.get("vector").nullable()?.checktable() ?: run {
            val array = location.position.toLuaValue()
            table.set("vector", array)
            array
        }
        vector.set(1, location.x.toLuaValue())
        vector.set(2, location.y.toLuaValue())
        vector.set(3, location.z.toLuaValue())
    }
}