package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.*
import org.lain.engine.transport.packet.InputActionDto.*
import org.lain.engine.util.Storage

@Serializable
sealed class InputActionDto {
    @Serializable
    data class SlotClick(val cursorItem: ItemUuid, val item: ItemUuid) : InputActionDto()
    @Serializable
    object Base : InputActionDto()
    @Serializable
    object Attack : InputActionDto()
}

@Serializable
data class InteractionDto(
    val id: InteractionId,
    val type: VerbTypeDto,
    val item: ItemUuid? = null,
    val raycastPlayer: PlayerId? = null,
    val action: InputActionDto,
    val timeElapsed: Int = 0
)

@Serializable
data class VerbTypeDto(
    val id: VerbId,
    val name: String,
    val time: Int,
    val target: VerbType.Target
)

fun InputAction.toDto(): InputActionDto = when(this) {
    is InputAction.SlotClick -> SlotClick(cursorItem.uuid, item.uuid)
    is InputAction.Attack -> Attack
    is InputAction.Base -> Base
}

fun InputActionDto.toDomain(itemStorage: Storage<ItemUuid, EngineItem>, ): InputAction {
    return when(this) {
        is SlotClick -> InputAction.SlotClick(
            itemStorage.get(cursorItem) ?: throw InvalidItemUuidException(cursorItem),
            itemStorage.get(item) ?: throw InvalidItemUuidException(item),
        )
        is Base -> InputAction.Base
        is Attack -> InputAction.Attack
    }
}

fun InteractionComponent.toDto(): InteractionDto = InteractionDto(
    id = id,
    type = type.toDto(),
    item = handItem?.uuid,
    raycastPlayer = raycastPlayer?.id,
    action = action.toDto(),
    timeElapsed = timeElapsed
)

fun InteractionDto.toDomain(
    itemStorage: Storage<ItemUuid, EngineItem>,
    playerStorage: Storage<PlayerId, EnginePlayer>,
): InteractionComponent {
    return InteractionComponent(
        id = id,
        type = type.toDomain(),
        handItem = item?.let { itemStorage.get(it) ?: throw InvalidItemUuidException(it) },
        raycastPlayer = raycastPlayer?.let { playerStorage.get(it) ?: error("Player $raycastPlayer not found") },
        action = action.toDomain(itemStorage),
        timeElapsed = timeElapsed
    )
}

fun VerbType.toDto(): VerbTypeDto = VerbTypeDto(
    id = id,
    name = name,
    time = time,
    target = target
)

fun VerbTypeDto.toDomain(): VerbType {
    return VerbType(
        id = id,
        name = name,
        time = time,
        target = target
    )
}

class InvalidItemUuidException(uuid: ItemUuid) : Exception("Предмет $uuid не найден")