package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.EngineId
import org.lain.engine.script.Identifiable
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.world.World
import java.util.concurrent.ConcurrentHashMap

/**
 * # Предмет модификации
 * Уникальный экземпляр предмета, представляемый в игре как единичный ItemStack
 */
typealias EngineItem = EntityId

@JvmInline
@Serializable
value class ItemId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toItemPrefabId() = ItemId(this)

class ItemStorage : Storage<PersistentId, EngineItem>() {
    override val map: MutableMap<PersistentId, EngineItem> = ConcurrentHashMap()
}