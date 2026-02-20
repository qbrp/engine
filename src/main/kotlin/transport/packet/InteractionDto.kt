package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemUuid
import org.lain.engine.player.InputAction
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.VerbId
import org.lain.engine.player.VerbType
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
    val type: VerbTypeDto,
    val item: ItemUuid? = null,
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
    type = type.toDto(),
    item = handItem?.uuid,
    action = action.toDto(),
    timeElapsed = timeElapsed
)

fun InteractionDto.toDomain(itemStorage: Storage<ItemUuid, EngineItem>): InteractionComponent {
    return InteractionComponent(
        type = type.toDomain(itemStorage),
        handItem = item?.let { itemStorage.get(it) ?: throw InvalidItemUuidException(it) },
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

fun VerbTypeDto.toDomain(itemStorage: Storage<ItemUuid, EngineItem>): VerbType {
    return VerbType(
        id = id,
        name = name,
        time = time,
        target = target
    )
}

class InvalidItemUuidException(uuid: ItemUuid) : Exception("Предмет $uuid не найден")