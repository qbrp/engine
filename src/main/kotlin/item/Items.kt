package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import java.util.concurrent.ConcurrentHashMap

/**
 * # Предмет модификации
 * Уникальный экземпляр предмета, представляемый в игре как единичный ItemStack
 */
typealias EngineItem = EntityId

data class UpdateMeta(var adaptedThisTick: Boolean = false) : Component

@JvmInline
@Serializable
value class ItemId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

class ItemStorage : Storage<String, EngineItem>(), ItemAccess {
    override val map: MutableMap<String, EngineItem> = ConcurrentHashMap()

    override fun getItem(uuid: PersistentId): EngineItem? {
        return this.get(uuid.value)
    }
}

interface ItemAccess {
    fun getItem(uuid: PersistentId): EngineItem?
}