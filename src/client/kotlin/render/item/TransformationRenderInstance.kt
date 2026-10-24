package org.lain.engine.client.render.item

import net.minecraft.world.item.ItemStack
import org.lain.engine.player.EquipmentSlotId
import java.util.UUID

sealed interface TransformationRenderInstance {
    data class Entity(val entityId: UUID) : TransformationRenderInstance

    data class Equipment(
        val playerId: UUID,
        val slotId: EquipmentSlotId,
    ) : TransformationRenderInstance

    class Stack(private val itemStack: ItemStack) : TransformationRenderInstance {
        override fun equals(other: Any?): Boolean =
            other is Stack && other.itemStack === itemStack

        override fun hashCode(): Int = System.identityHashCode(itemStack)
    }
}
