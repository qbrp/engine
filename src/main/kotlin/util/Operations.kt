package org.lain.engine.util

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.script.*
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.component.EntityId
import org.lain.engine.util.math.Pos
import org.lain.engine.world.VoxelPos

data class Operation(
    val id: OperationId,
    val name: String,
    val script: Script<ScriptContext.OperationExecution, *>,
    val inputs: List<AnyInput>,
    val actors: List<OperationActor.Type> = OperationActor.Type.entries,
    val permission: String? = null,
)

typealias AnyInput = Input<*>

data class Input<T : Any>(val id: String, val type: Type<T>) {
    fun valueOf(value: T) = InputValue(this, value)

    sealed class Type<T : Any>() {
        data class Text(val isSingleWord: Boolean) : Type<String>()
        object Integer : Type<Int>()
        object Double : Type<Double>()
        object Logic : Type<Boolean>()
        object Table : Type<List<Any>>()
    }
}

data class InputValue<T : Any>(val input: Input<T>, val value: T) {
    val booleanValue get() = if (input.type is Input.Type.Logic) value as Boolean else raiseTypeError()
    val intValue get() = if (input.type is Input.Type.Integer) value as Int else raiseTypeError()
    val doubleValue get() = if (input.type is Input.Type.Double) value as Double else raiseTypeError()
    val stringValue get() = if (input.type is Input.Type.Text) value as String else raiseTypeError()
    val tableValue get() = if (input.type is Input.Type.Table) value as List<Any> else raiseTypeError()

    private fun raiseTypeError(): Nothing = error("Invalid type: ${input.type}")
}

typealias AnyInputValue = InputValue<*>

data class OperationSelection(
    val pos1: VoxelPos,
    val pos2: VoxelPos
)

data class OperationTarget(
    val player: EnginePlayer?,
    val voxelPos: VoxelPos,
    val pos: Pos
)

data class OperationActor(
    val type: Type,
    val player: EnginePlayer,
    val entity: EntityId
) {
    @Serializable
    enum class Type {
        COMMAND, TOOLGUN
    }
}

@JvmInline
@Serializable
value class OperationId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toOperationId() = OperationId(this)

fun Operation.execute(
    ctx: ScriptContext.OperationExecution,
    handler: ServerHandler? = null,
): ExecutionResult<*> {
    val result = script.execute(ctx)
    handler?.onPlayerOperation(ctx, this)
    return result
}
