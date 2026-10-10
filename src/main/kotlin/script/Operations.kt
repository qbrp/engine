package org.lain.engine.script

import kotlinx.serialization.Serializable
import org.lain.engine.player.EnginePlayer
import org.lain.engine.server.ServerHandler
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.Pos
import org.lain.engine.world.VoxelPos

data class Operation(
    val id: OperationId,
    val name: String,
    val script: Script<ScriptContext.OperationExecution, *>,
    val inputs: List<Input>,
    val actors: List<OperationActor.Type> = OperationActor.Type.entries,
    val permission: String? = null,
)

data class Input(val id: String, val type: InputType) {
    fun valueOf(value: ScriptValue) = InputValue(id, value)
}

sealed interface InputType {
    data object Logic : InputType

    data class Integer(
        val min: Int? = null,
        val max: Int? = null,
    ) : InputType {
        init {
            require(min == null || max == null || min <= max) {
                "Minimum input value $min exceeds maximum $max"
            }
        }
    }

    data class Double(
        val min: kotlin.Double? = null,
        val max: kotlin.Double? = null,
    ) : InputType {
        init {
            require(min == null || max == null || min <= max) {
                "Minimum input value $min exceeds maximum $max"
            }
        }
    }

    data object Table : InputType

    data class Text(val isSingleWord: Boolean = false) : InputType

    data class Selection(val variants: SelectionVariants) : InputType {
        constructor(entries: List<SelectionEntry>) : this(SelectionVariants.Static(entries))

        init {
            if (variants is SelectionVariants.Static) {
                require(variants.entries.isNotEmpty()) {
                    "Selection input must contain at least one variant"
                }
            }
        }
    }
}

data class SelectionEntry(
    val id: String,
    val value: ScriptValue,
)

sealed interface SelectionVariants {
    data class Static(val entries: List<SelectionEntry>) : SelectionVariants {
        init {
            require(entries.distinctBy(SelectionEntry::id).size == entries.size) {
                "Selection variant ids must be unique"
            }
        }
    }

    data class Dynamic(
        val script: Script<ScriptContext.OperationInputResolution, ScriptValue>,
    ) : SelectionVariants
}

fun SelectionVariants.resolve(
    context: ScriptContext.OperationInputResolution,
): ExecutionResult<List<SelectionEntry>> = when (this) {
    is SelectionVariants.Static -> ExecutionResult.Success(entries)
    is SelectionVariants.Dynamic -> when (val result = script.execute(context)) {
        is ExecutionResult.Failure -> ExecutionResult.Failure(result.error)
        is ExecutionResult.Success -> runCatching {
            result.value.toSelectionEntries()
        }.fold(
            onSuccess = { ExecutionResult.Success(it) },
            onFailure = { ExecutionResult.Failure(it) },
        )
    }
}

internal fun ScriptValue.toSelectionEntries(): List<SelectionEntry> {
    if (this is SNil) return emptyList()

    val list = this as? SList
        ?: error("Dynamic selection variants must return a list")
    return SelectionVariants.Static(
        list.values.mapIndexed { index, value ->
            val table = value as? STable
                ?: error("Selection variant at index ${index + 1} must be a table")
            val id = (table.values[SString("id")] as? SString)?.value
                ?: error("Selection variant at index ${index + 1} must contain a string id")
            SelectionEntry(id, table.values[SString("value")] ?: SNil)
        }
    ).entries
}

@Serializable
data class InputValue(val id: String, val value: ScriptValue)

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
