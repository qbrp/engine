package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.ComponentManager
import org.lain.engine.util.ComponentState
import org.lain.engine.util.Entity
import org.lain.engine.util.Storage
import java.util.*
import java.util.concurrent.ConcurrentHashMap

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
) : Entity, ComponentManager by state {
    override val stringId: String
        get() = this.uuid.toString()
}

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
    override fun toString(): String {
        return value
    }

    companion object {
        fun next() = ItemUuid(UUID.randomUUID().toString())
        fun fromString(string: String): ItemUuid = ItemUuid(string)
    }
}

class ItemStorage : Storage<ItemUuid, EngineItem>(), ItemAccess {
    override val map: MutableMap<ItemUuid, EngineItem> = ConcurrentHashMap()

    override fun getItem(uuid: ItemUuid): EngineItem? {
        return this.get(uuid)
    }
}

interface ItemAccess {
    fun getItem(uuid: ItemUuid): EngineItem?
}