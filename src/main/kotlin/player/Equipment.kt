package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.script.EngineId
import org.lain.engine.script.Identifiable
import org.lain.engine.util.ecs.componentType

@JvmInline
@Serializable
value class EquipmentSlotId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
}

fun EngineId.toEquipmentSlotId() = EquipmentSlotId(this)

@Serializable
data class EquipmentSlot(
    val name: String,
    val dependsEyeY: Boolean = false,
    val part: PlayerPart
)

/**
 * Сущности хранятся через связь-владение.
 * Гарантируется репликация вместе с сущностью игроком. Сохранеие в БД происходит отдельно
 * @see org.lain.engine.server.replication.collectReplicationEntities
 * */
data class Equipment(val slots: MutableMap<EquipmentSlotId, EntityId>) : Component {
    companion object {
        val TYPE = componentType<Equipment>()
    }
}

val EnginePlayer.equipment
    get() = require<Equipment>().slots


@Serializable
data class Equippable(
    val display: OutfitDisplay = OutfitDisplay.Separated,
) : Component

@Serializable
sealed class OutfitDisplay {
    @Serializable
    object Separated : OutfitDisplay()
}