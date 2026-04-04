
package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.getVoidScript
import org.lain.engine.script.interactionScriptContext
import org.lain.engine.util.NamespacedStorage
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.handle
import org.lain.engine.util.component.require

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
        BASE_SCRIPT_VERB.takeIf { scriptBindings.base != null && raycastPlayerNotNull(player, SOCIAL_INTERACTION_DISTANCE) }
    }
    forAction<InputAction.Attack> {
        ATTACK_SCRIPT_VERB.takeIf { scriptBindings.attack != null && raycastPlayerNotNull(player, SOCIAL_INTERACTION_DISTANCE) }
    }
}

context(contents: NamespacedStorage, interaction: InteractionComponent)
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