package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.has
import org.lain.engine.util.require

@Serializable
data class Count(var value: Int, val max: Int) : Component

val EngineItem.count
    get() = this.require<Count>().value

val EngineItem.maxCount
    get() = this.require<Count>().max

/**
 * Попробовать совместить предметы - наслоить `mergeItem` на `baseItem`.
 * В случае успешного совмещения компонент количества `mergeItem` ставится на 0. **Предмет должен быть удалён**
 * @return Были ли совмещены предметы
 */
fun merge(baseItem: EngineItem, mergeItem: EngineItem): Boolean {
    val similarKind = baseItem.id == mergeItem.id
    val countable = baseItem.has<Count>() && mergeItem.has<Count>()
    return similarKind && countable
}

