package org.lain.engine.player

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.handle
import org.lain.cyberia.ecs.require
import org.lain.engine.script.*
import org.lain.engine.script.lua.LuaEntityComponent
import org.lain.engine.script.lua.luaValue
import org.lain.engine.server.Notification
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.component.EntityId
import org.lain.engine.world.World
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.util.IdentityHashMap
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

data class DeveloperMode(var enabled: Boolean, var acoustic: Boolean = false) : Component

var EnginePlayer.developerMode
    get() = this.require<DeveloperMode>().enabled
    set(value) {
        this.require<DeveloperMode>().enabled = value
    }

var EnginePlayer.acousticDebug
    get() = this.require<DeveloperMode>().let { it.acoustic && it.enabled }
    set(value) {
        this.require<DeveloperMode>().acoustic = value
    }

@Serializable
data class ScriptBindings(
    var attack: ScriptId? = null,
    var base: ScriptId? = null,
) : Component

val ATTACK_SCRIPT_VERB = VerbType("attack_script", "Вызвать скрипт атаки")
val BASE_SCRIPT_VERB = VerbType("base_script", "Вызвать скрипт взаимодействия")

fun appendScriptBindingVerbs(player: EnginePlayer) = player.handle<VerbLookup>() {
    val scriptBindings = player.require<ScriptBindings>()
    forAction<InputAction.Base> {
        BASE_SCRIPT_VERB.takeIf {
            scriptBindings.base != null && raycastPlayerNotNull(
                player,
                SOCIAL_INTERACTION_DISTANCE
            )
        }
    }
    forAction<InputAction.Attack> {
        ATTACK_SCRIPT_VERB.takeIf {
            scriptBindings.attack != null && raycastPlayerNotNull(
                player,
                SOCIAL_INTERACTION_DISTANCE
            )
        }
    }
}

context(contents: NamespacedStorageAccess, interaction: InteractionComponent)
fun handleHandScriptInteractions(player: EnginePlayer) {
    val bindings = player.require<ScriptBindings>()
    player.handleInteraction(ATTACK_SCRIPT_VERB) {
        contents
            .getVoidScript<ScriptContext.Interaction>(bindings.attack!!)
            ?.execute(player.interactionScriptContext)
        complete()
    }
    player.handleInteraction(BASE_SCRIPT_VERB) {
        contents
            .getVoidScript<ScriptContext.Interaction>(bindings.base!!)
            ?.execute(player.interactionScriptContext)
        complete()
    }
}