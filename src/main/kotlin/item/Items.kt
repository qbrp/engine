package org.lain.engine.item

import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.Storage
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * # Предмет модификации
 * Уникальный экземпляр предмета, представляемый в игре как единичный ItemStack
 * @param id Идентификатор префаба предмета
 * @param uuid Уникальный идентификатор экземпляра
 */
data class EngineItem(
    val id: ItemId,
    val uuid: ItemUuid,
    val state: ComponentState
) : ComponentManager by state

@JvmInline
value class ItemId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

@JvmInline
value class ItemUuid(val value: UUID)

class ItemStorage : Storage<ItemUuid, EngineItem>()