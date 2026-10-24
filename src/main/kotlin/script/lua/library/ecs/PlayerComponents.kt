package org.lain.engine.script.lua.library.ecs

import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.player.CustomPlayerAttributes
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.player.PlayerModeComponent
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.player.Velocity
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.player.require
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.LuaUserdataType
import org.lain.engine.script.lua.library.LuaComponentBinding
import org.lain.engine.script.lua.library.coerceToLua
import org.lain.engine.script.lua.library.setLuaScriptComponent
import org.lain.engine.script.lua.luaBool
import org.lain.engine.script.lua.luaNum
import org.lain.engine.script.lua.luaStr
import org.lain.engine.script.lua.luaTable
import org.lain.engine.script.lua.luaTableOf
import org.lain.engine.script.lua.nullable
import org.lain.engine.script.lua.toLuaList
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.script.lua.toScriptValue
import org.lain.engine.util.ecs.componentType
import org.lain.engine.world.World
import org.lain.engine.world.location
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
            "fly_speed" -> self.flySpeed.toDouble().luaNum()
            "jump_strength" -> self.jumpStrength.toDouble().luaNum()
            "gravity" -> self.gravity.toDouble().luaNum()
            else -> NIL
        }
    }
    newIndexSelf { self, key, value ->
        when(key.tojstring()) {
            "speed" -> self.speed = value.checknumber().tofloat()
            "fly_speed" -> self.flySpeed = value.checknumber().tofloat()
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

object PlayerAttributesBinding : LuaComponentBinding<PlayerAttributes> {
    override val componentType: ComponentType<PlayerAttributes>
        get() = componentType<PlayerAttributes>()
    override val id = EngineId("core/player/attributes")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: PlayerAttributes) =
        lua.playerAttributesMetaTable.newInstance(component)
}

object PlayerCustomAttributesBinding : LuaComponentBinding<CustomPlayerAttributes> {
    override val componentType: ComponentType<CustomPlayerAttributes>
        get() = componentType<CustomPlayerAttributes>()
    override val id = EngineId("core/player/custom_attributes")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: CustomPlayerAttributes) =
        lua.playerCustomAttributesMetaTable.newInstance(component)
}

object MovementStatusBinding : LuaComponentBinding<MovementStatus> {
    override val componentType: ComponentType<MovementStatus>
        get() = componentType<MovementStatus>()
    override val id = EngineId("core/player/movement_status")

    context(lua: LuaScriptEngine)
    override fun toLua(world: World?, entity: EntityId, component: MovementStatus) =
        lua.playerMovementStatusMetaTable.newInstance(component)
}

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
