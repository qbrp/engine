package org.lain.engine.transport.packet

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.item.ItemAccess
import org.lain.engine.player.*
import org.lain.engine.storage.PersistentId
import org.lain.engine.transport.packet.InputActionDto.*
import org.lain.engine.util.Storage
import org.lain.engine.world.World

@Serializable
sealed class InputActionDto {
    @Serializable
    data class SlotClick(val cursorItem: PersistentId, val item: PersistentId) : InputActionDto()
    @Serializable
    object Base : InputActionDto()
    @Serializable
    object Attack : InputActionDto()
    @Serializable
    object TakeOff : InputActionDto()
}

@Serializable
data class InteractionDto(
    val id: InteractionId,
    val type: VerbType,
    val item: PersistentId? = null,
    val handFree: Boolean,
    val raycastPlayer: PlayerId? = null,
    val action: InputActionDto,
    val timeElapsed: Int = 0
)

context(world: World)
fun InputAction.toDto(): InputActionDto = when(this) {
    is InputAction.SlotClick -> SlotClick(cursorItem.requireComponent(), item.requireComponent())
    is InputAction.Attack -> Attack
    is InputAction.Base -> Base
    is InputAction.TakeOff -> TakeOff
}

fun InputActionDto.toDomain(itemStorage: ItemAccess): InputAction {
    return when(this) {
        is SlotClick -> InputAction.SlotClick(
            itemStorage.getItem(cursorItem) ?: throw InvalidPersistentIdException(cursorItem),
            itemStorage.getItem(item) ?: throw InvalidPersistentIdException(item),
        )
        is Base -> InputAction.Base
        is Attack -> InputAction.Attack
        is TakeOff -> InputAction.TakeOff
    }
}

context(world: World)
fun InteractionComponent.toDto(): InteractionDto = InteractionDto(
    id = id,
    type = type,
    item = handItem?.requireComponent<PersistentId>(),
    handFree = handFree,
    raycastPlayer = raycastPlayer?.id,
    action = action.toDto(),
    timeElapsed = timeElapsed
)

fun InteractionDto.toDomain(
    itemStorage: ItemAccess,
    playerStorage: Storage<PlayerId, EnginePlayer>,
): InteractionComponent {
    return InteractionComponent(
        id = id,
        type = type,
        handItem = item?.let { itemStorage.getItem(it) ?: throw InvalidPersistentIdException(it) },
        handFree = handFree,
        raycastPlayer = raycastPlayer?.let { playerStorage.get(it) ?: error("Player $raycastPlayer not found") },
        action = action.toDomain(itemStorage),
        timeElapsed = timeElapsed,
    )
}

class InvalidPersistentIdException(uuid: PersistentId) : Exception("Предмет $uuid не найден")