package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getAll
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.world.World

@Serializable
data class Count(var value: Int, val max: Int) : Component

/**
 * Можно ли совместить предметы `mergeItem` и `baseItem`.
 * @return Были ли совмещены предметы
 */
fun World.merge(baseItem: EngineItem, mergeItem: EngineItem): Boolean {
    val similarKind = baseItem.requireComponent<Item>().id == mergeItem.requireComponent<Item>().id
    if (!similarKind) return false
    val countable = baseItem.hasComponent<Count>() && mergeItem.hasComponent<Count>()
    if (!countable) return false

    return baseItem.getAll().toSet() == mergeItem.getAll().toSet()
}

context(world: World)
fun EngineItem.getCount() = this.getComponent<Count>()?.value ?: 1

