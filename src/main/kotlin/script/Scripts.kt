package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.item.EngineItem
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.interaction.PlayerInput
import org.lain.engine.util.AnyInputValue
import org.lain.engine.util.OperationActor
import org.lain.engine.util.OperationSelection
import org.lain.engine.util.OperationTarget
import org.lain.engine.world.VoxelMeta
import org.lain.engine.world.VoxelPos
import org.lain.engine.world.World as EngineWorld

interface OperationBehaviour {
    fun generateTarget(): OperationTarget
    fun generateSelection(): OperationSelection?
    fun feedback(string: String)
}

interface ScriptContext {
    data class Player(val player: EnginePlayer) : ScriptContext
    data class World(val world: EngineWorld) : ScriptContext
    data class Item(val world: EngineWorld, val item: EngineItem) : ScriptContext
    data class PlayerInputTick(val player: EnginePlayer, val input: PlayerInput) : ScriptContext
    data class VoxelAction(
        val player: EnginePlayer?,
        val world: EngineWorld,
        val pos: VoxelPos,
        val meta: VoxelMeta
    ) : ScriptContext
    data class OperationExecution(
        val actor: OperationActor,
        val target: OperationTarget? = null,
        val inputValues: List<AnyInputValue>,
        val behaviour: OperationBehaviour
    ) : ScriptContext
    interface SystemEntityHandle : ScriptContext {
        val components: Collection<ScriptComponent>
        val world: EngineWorld
        val entity: EntityId
    }
}

val EnginePlayer.scriptContext: ScriptContext.Player
    get() = ScriptContext.Player(this)

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
value class ScriptId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toScriptId(): ScriptId = ScriptId(this)

@Suppress("UNCHECKED_CAST")
fun <C : ScriptContext, R : Any> NamespacedStorageAccess.getScript(id: ScriptId): Script<C, R>? {
    return scripts[id] as? Script<C, R>
}

fun <C : ScriptContext> NamespacedStorageAccess.getVoidScript(id: ScriptId): Script<C, Unit>? {
    return getScript(id)
}
