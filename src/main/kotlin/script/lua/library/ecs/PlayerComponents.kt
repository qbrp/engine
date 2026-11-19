package org.lain.engine.script.lua.library.ecs

import org.lain.engine.player.*
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.*
import org.lain.engine.script.lua.library.ComponentBinding
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.setLuaScriptComponent
import org.lain.engine.world.World
import org.luaj.vm2.LuaValue.NIL

fun PlayerModeMetaTable() = LuaUserdataType<PlayerModeComponent> {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "mode" -> when {
                self.isGameMaster -> "game_master"
                self.isSpectator -> "spectator"
                else -> "default"
            }.luaStr()
            "is_spectator" -> self.isSpectator.luaBool()
            "is_game_master" -> self.isGameMaster.luaBool()
            else -> NIL
        }
    }
}

context(lua: LuaScriptEngine)
fun PlayerPhysicsMetaTable() = LuaUserdataType<PlayerPhysics> {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "no_clip" -> self.noClip.luaBool()
            "collides" -> self.collides.toLuaList { it.coerceToLua() }
            else -> NIL
        }
    }
    newIndexSelf { self, key, value ->
        when(key.tojstring()) {
            "no_clip" -> self.noClip = value.checkboolean()
        }
    }
}

fun PlayerInputMetaTable() = LuaUserdataType<PlayerInput>() {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "is_sprinting" -> self.isSprinting.luaBool()
            else -> NIL
        }
    }
}

fun PlayerAttributesMetaTable() = LuaUserdataType<PlayerAttributes>() {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "speed" -> self.speed.toDouble().luaNum()
            "fly_speed" -> self.flyingSpeed.toDouble().luaNum()
            "jump_strength" -> self.jumpStrength.toDouble().luaNum()
            "gravity" -> self.gravity.toDouble().luaNum()
            else -> NIL
        }
    }
    newIndexSelf { self, key, value ->
        when(key.tojstring()) {
            "speed" -> self.speed = value.checknumber().tofloat()
            "fly_speed" -> self.flyingSpeed = value.checknumber().tofloat()
            "jump_strength" -> self.jumpStrength = value.checknumber().tofloat()
            "gravity" -> self.gravity = value.checknumber().tofloat()
        }
    }
}

context(lua: LuaScriptEngine)
fun PlayerCustomAttributesMetaTable() = LuaUserdataType<CustomPlayerAttributes> {
    newIndexSelf { self, key, value ->
        when(key.tojstring()) {
            "speed" -> self.speed = value.nullable()?.checknumber()?.tofloat()
            "jump_strength" -> self.jumpStrength = value.nullable()?.checknumber()?.tofloat()
            "gravity" -> self.gravity = value.nullable()?.checknumber()?.tofloat()
            else -> self.script[key.tojstring()] = value.toScriptValue()
        }
    }
    indexSelf { self, key ->
        when(key.tojstring()) {
            "speed" -> self.speed?.toDouble()?.luaNum() ?: NIL
            "jump_strength" -> self.jumpStrength?.toDouble()?.luaNum() ?: NIL
            "gravity" -> self.gravity?.toDouble()?.luaNum() ?: NIL
            else -> self.script[key.tojstring()]?.toLuaValue() ?: NIL
        }
    }
}

fun MovementStatusMetaTable() = LuaUserdataType<MovementStatus>() {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "intention" -> self.intention.toDouble().luaNum()
            "stamina" -> self.stamina.toDouble().luaNum()
            else -> NIL
        }
    }
    newIndexSelf { self, key, value ->
        when(key.tojstring()) {
            "stamina" -> self.stamina = value.checknumber().tofloat()
        }
    }
}

fun PlayerAttributesBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/player/attributes"),
    componentClass = PlayerAttributes::class,
    cache = cache,
    toLuaValue = { _, _, component -> playerAttributesMetaTable.newInstance(component) },
)

fun PlayerCustomAttributesBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/player/custom_attributes"),
    componentClass = CustomPlayerAttributes::class,
    cache = cache,
    toLuaValue = { _, _, component -> playerCustomAttributesMetaTable.newInstance(component) },
)

fun MovementStatusBinding(cache: Boolean = false) = ComponentBinding(
    id = EngineId("core/player/movement_status"),
    componentClass = MovementStatus::class,
    cache = cache,
    toLuaValue = { _, _, component -> playerMovementStatusMetaTable.newInstance(component) },
)

context(luaScriptEngine: LuaScriptEngine)
fun PlayerVelocityMetaTable() = LuaUserdataType<Velocity>() {
    indexSelf { self, key ->
        when(key.tojstring()) {
            "motion" -> self.motion.coerceToLua()
            "previous" -> self.prev.coerceToLua()
            else -> NIL
        }
    }
}

context(luaScriptEngine: LuaScriptEngine)
private fun LuaPlayerComponent(player: EnginePlayer) = luaTable {
    "object"(player.coerceToLua())
}

context(world: World, lua: LuaScriptEngine)
fun EnginePlayer.setupLuaScriptComponents() {
    entity.setLuaScriptComponent(
        LuaPlayerComponent(this),
        CoreScriptComponents.PLAYER
    )
    entity.setLuaScriptComponent(
        LuaPlayerInventoryComponent(this),
        CoreScriptComponents.PLAYER_INVENTORY
    )
    entity.setLuaScriptComponent(
        lua.playerPhysicsMetaTable.newInstance(require()),
        CoreScriptComponents.PLAYER_PHYSICS
    )
    entity.setLuaScriptComponent(
        lua.playerModeMetaTable.newInstance(require()),
        CoreScriptComponents.PLAYER_MODE
    )
    entity.setLuaScriptComponent(
        lua.playerInputMetaTable.newInstance(require()),
        CoreScriptComponents.PLAYER_INPUT
    )
    entity.setLuaScriptComponent(
        lua.playerVelocityMetaTable.newInstance(require()),
        CoreScriptComponents.PLAYER_VELOCITY
    )
}
