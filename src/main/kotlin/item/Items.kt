package org.lain.engine.item

import kotlinx.serialization.Serializable
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
@Serializable
value class ItemId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

@JvmInline
@Serializable
value class ItemUuid(val value: String) {
    companion object {
        fun next() = ItemUuid(UUID.randomUUID().toString())
    }
}

class ItemStorage : Storage<ItemUuid, EngineItem>()