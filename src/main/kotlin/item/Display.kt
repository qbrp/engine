package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.world.World

/**
 * @param text Отображаемый текст без форматирования
 */
@Serializable
data class ItemName(val text: String) : Component

context(world: World)
fun EngineItem.getName(): String = getComponent<ItemName>()?.text ?: run {
    val c = ItemName("Предмет")
    setComponent(c)
    c.text
}