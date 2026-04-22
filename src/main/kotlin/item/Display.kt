package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.world.World

/**
 * @param text Отображаемый текст без форматирования
 */
@Serializable
data class ItemName(val text: String) : ItemComponent

context(world: World)
fun EngineItem.getName(): String = requireComponent<ItemName>().text