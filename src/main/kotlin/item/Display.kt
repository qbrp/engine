package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.require

/**
 * @param text Отображаемый текст без форматирования
 */
@Serializable
data class ItemName(val text: String) : Component

val EngineItem.name
    get() = this.get<ItemName>()?.text ?: "Предмет"