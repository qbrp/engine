package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.transport.packet.ItemComponent
import org.lain.cyberia.ecs.get

/**
 * @param text Отображаемый текст без форматирования
 */
@Serializable
data class ItemName(val text: String) : ItemComponent

val EngineItem.name
    get() = this.get<ItemName>()?.text ?: "Предмет"