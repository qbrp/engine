package org.lain.engine.item

import org.lain.engine.util.Component
import org.lain.engine.util.require

/**
 * @param text Отображаемый текст без форматирования
 */
data class ItemName(val text: String) : Component

val EngineItem.name
    get() = this.require<ItemName>().text