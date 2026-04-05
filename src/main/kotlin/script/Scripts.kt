package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.InteractionComponent
import org.lain.engine.util.NamespacedStorage
import org.lain.cyberia.ecs.require

sealed class ScriptContext {
    data class Player(val player: EnginePlayer) : ScriptContext()
    data class Interaction(
        val player: EnginePlayer,
        val raycastPlayer: EnginePlayer?,
    ) : ScriptContext()
    data class World(val world: org.lain.engine.world.World) : ScriptContext()
}

val EnginePlayer.scriptContext: ScriptContext.Player
    get() = ScriptContext.Player(this)

val EnginePlayer.interactionScriptContext: ScriptContext.Interaction
    get() = ScriptContext.Interaction(this, require<InteractionComponent>().raycastPlayer)

sealed class ExecutionResult<R> {
    data class Success<R>(val result: R) : ExecutionResult<R>()
    data class Failure<R>(val error: Throwable) : ExecutionResult<R>()
}

interface Script<C : ScriptContext, R : Any> {
    fun execute(context: C) : ExecutionResult<R>
}

typealias VoidScript<C> = Script<C, Unit>

@JvmInline
@Serializable
value class ScriptId(val string: String) {
    override fun toString(): String = string
}

fun String.toScriptId(): ScriptId = ScriptId(this)

@Suppress("UNCHECKED_CAST")
fun <C : ScriptContext, R : Any> NamespacedStorage.getScript(id: ScriptId): LuaScript<C, R>? {
    return scripts[id] as? LuaScript<C, R>
}

fun <C : ScriptContext> NamespacedStorage.getVoidScript(id: ScriptId): LuaScript<C, Unit>? {
    return getScript(id)
}