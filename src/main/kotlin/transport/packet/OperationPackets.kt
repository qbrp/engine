package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.mc.commands.CommandOperationBehaviour
import org.lain.engine.player.PlayerId
import org.lain.engine.script.ScriptContext
import org.lain.engine.transport.Endpoint
import org.lain.engine.transport.Packet
import org.lain.engine.script.InputValue
import org.lain.engine.script.OperationActor
import org.lain.engine.script.OperationId
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
    val inputValues: List<InputValue>,
    val behaviour: OperationBehaviourDto
)

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
    inputValues,
    when(behaviour) {
        is CommandOperationBehaviour -> OperationBehaviourDto.Command
        else -> error("Unsupported behaviour $behaviour")
    }
)

val CLIENTBOUND_OPERATION_ENDPOINT = Endpoint<OperationPacket>()
