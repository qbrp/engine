package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.mc.CommandIntentBehaviour
import org.lain.engine.player.PlayerId
import org.lain.engine.script.ScriptContext
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.util.Input
import org.lain.engine.util.Input.Type
import org.lain.engine.util.InputValue
import org.lain.engine.util.IntentActor
import org.lain.engine.util.IntentId
import org.lain.engine.util.math.ImmutableVec3
import org.lain.engine.world.ImmutableVoxelPos

@Serializable
data class IntentPacket(
    val intent: IntentId,
    val dto: IntentExecuteDto
) : Packet

@Serializable
data class IntentExecuteDto(
    val actor: IntentActorDto,
    val target: IntentTargetDto?,
    val inputValues: List<InputValueDto>,
    val behaviour: IntentBehaviourDto
)

fun InputValue<*>.toDto(): InputValueDto {
    val type = when (this.input.type) {
        Type.Double -> InputValueDto.Value.Double(doubleValue)
        Type.Integer -> InputValueDto.Value.Integer(intValue)
        Type.Logic -> InputValueDto.Value.Logic(booleanValue)
        Type.Table -> InputValueDto.Value.Table(tableValue.map { it.toDto() })
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
        @Serializable data class Table(val value: List<InputValueDto>) : Value<List<InputValueDto>>()
        @Serializable data class Text(val value: String) : Value<String>()
    }

    fun toDomain(): InputValue<Any> {
        val (value, type) = when (val v = this.value) {
            is Value.Double -> v.value to Input.Type.Double
            is Value.Integer -> v.value to Input.Type.Integer
            is Value.Logic -> v.value to Input.Type.Logic
            is Value.Table -> v.value.map { it.toDomain() } to Input.Type.Table
            is Value.Text -> v.value to Input.Type.Text(false)
        }
        return InputValue(
            Input(id, type as Type<Any>),
            value
        )
    }
}

@Serializable
sealed class IntentBehaviourDto {
    @Serializable
    object Command : IntentBehaviourDto()
}

@Serializable
data class IntentTargetDto(
    val player: PlayerId?,
    val voxelPos: ImmutableVoxelPos,
    val pos: ImmutableVec3
)

@Serializable
data class IntentActorDto(
    val type: IntentActor.Type,
    val player: PlayerId,
)

fun ScriptContext.IntentExecution.toDto() = IntentExecuteDto(
    IntentActorDto(
        actor.type,
        actor.player.id
    ),
    target?.let { target ->
        IntentTargetDto(
            target.player?.id,
            ImmutableVoxelPos(target.voxelPos),
            ImmutableVec3(target.pos)
        )
    },
    inputValues.map { it.toDto() },
    when(behaviour) {
        is CommandIntentBehaviour -> IntentBehaviourDto.Command
        else -> error("Unsupported behaviour $behaviour")
    }
)

val CLIENTBOUND_INTENT_ENDPOINT = Endpoint<IntentPacket>()