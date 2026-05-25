package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.world.World
import java.util.concurrent.ConcurrentHashMap

/**
 * # Предмет модификации
 * Уникальный экземпляр предмета, представляемый в игре как единичный ItemStack
 */
typealias EngineItem = EntityId

data class UpdateMeta(var adaptedThisTick: Boolean = false) : Component

context(world: World)
fun EngineItem.getUpdateMeta() = getComponent<UpdateMeta>() ?: UpdateMeta().also { setComponent(it) }

@JvmInline
@Serializable
value class ItemId(val value: String) {
    init { require(!value.contains(" ")) { "Идентификатор содержит пробелы" } }

    override fun toString(): String {
        return value
    }
}

class ItemStorage : Storage<PersistentId, EngineItem>(), ItemAccess {
    override val map: MutableMap<PersistentId, EngineItem> = ConcurrentHashMap()

    override fun getItem(uuid: PersistentId): EngineItem? {
        return this.get(uuid)
    }
}

interface ItemAccess {
    fun getItem(uuid: PersistentId): EngineItem?
}