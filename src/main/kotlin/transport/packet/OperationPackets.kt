package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.mc.commands.CommandOperationBehaviour
import org.lain.engine.player.PlayerId
import org.lain.engine.script.ScriptContext
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.Input
import org.lain.engine.util.Input.Type
import org.lain.engine.util.InputValue
import org.lain.engine.util.OperationActor
import org.lain.engine.util.OperationId
import org.lain.engine.util.math.ImmutableEVec3
import org.lain.engine.world.ImmutableVoxelPos

@Serializable
data class OperationPacket(
    val operation: OperationId,
    val dto: OperationExecuteDto
) : Packet

@Serializable
data class OperationExecuteDto(
    val actor: OperationActorDto,
    val target: OperationTargetDto?,
    val inputValues: List<InputValueDto>,
    val behaviour: OperationBehaviourDto
)

fun InputValue<*>.toDto(): InputValueDto {
    val type = when (this.input.type) {
        Type.Double -> InputValueDto.Value.Double(doubleValue)
        Type.Integer -> InputValueDto.Value.Integer(intValue)
        Type.Logic -> InputValueDto.Value.Logic(booleanValue)
        Type.Table -> TODO()
        is Type.Text -> InputValueDto.Value.Text(stringValue)
    }
    return InputValueDto(this.input.id, type)
}

@Serializable
data class InputValueDto(val id: String, val value: Value<*>) {
    @Serializable
    sealed class Value<T : Any> {
        @Serializable data class Integer(val value: Int) : Value<Int>()
        @Serializable data class Double(val value: kotlin.Double) : Value<Double>()
        @Serializable data class Logic(val value: Boolean) : Value<Boolean>()
        @Serializable data class Table(val value: Map<String, InputValueDto>) : Value<Map<String, InputValueDto>>()
        @Serializable data class Text(val value: String) : Value<String>()
    }

    fun toDomain(): InputValue<Any> {
        val (value, type) = when (val v = this.value) {
            is Value.Double -> v.value to Input.Type.Double
            is Value.Integer -> v.value to Input.Type.Integer
            is Value.Logic -> v.value to Input.Type.Logic
            is Value.Text -> v.value to Input.Type.Text(false)
            is Value.Table -> v.value.map { it.key to it.value } to Input.Type.Table
        }
        return InputValue(
            Input(id, type as Type<Any>),
            value
        )
    }
}

@Serializable
sealed class OperationBehaviourDto {
    @Serializable
    object Command : OperationBehaviourDto()
}

@Serializable
data class OperationTargetDto(
    val player: PlayerId?,
    val voxelPos: ImmutableVoxelPos,
    val pos: ImmutableEVec3
)

@Serializable
data class OperationActorDto(
    val type: OperationActor.Type,
    val player: PlayerId,
)

fun ScriptContext.OperationExecution.toDto() = OperationExecuteDto(
    OperationActorDto(
        actor.type,
        actor.player.id
    ),
    target?.let { target ->
        OperationTargetDto(
            target.player?.id,
            ImmutableVoxelPos(target.voxelPos),
            ImmutableEVec3(target.pos)
        )
    },
    inputValues.map { it.toDto() },
    when(behaviour) {
        is CommandOperationBehaviour -> OperationBehaviourDto.Command
        else -> error("Unsupported behaviour $behaviour")
    }
)

val CLIENTBOUND_OPERATION_ENDPOINT = Endpoint<OperationPacket>()
