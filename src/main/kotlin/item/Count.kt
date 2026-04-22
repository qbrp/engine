package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.world.World

@Serializable
data class Count(var value: Int, val max: Int) : ItemComponent

/**
 * Попробовать совместить предметы - наслоить `mergeItem` на `baseItem`.
 * В случае успешного совмещения компонент количества `mergeItem` ставится на 0. **Предмет должен быть удалён**
 * @return Были ли совмещены предметы
 */
fun World.merge(baseItem: EngineItem, mergeItem: EngineItem): Boolean {
    val similarKind = baseItem.requireComponent<ItemMeta>().id == mergeItem.requireComponent<ItemMeta>().id
    val countable = baseItem.hasComponent<Count>() && mergeItem.hasComponent<Count>()
    return similarKind && countable
}

context(world: World)
fun EngineItem.getCount() = this.getComponent<Count>()?.value ?: 1

