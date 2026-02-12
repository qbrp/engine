package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.util.Component
import org.lain.engine.util.get
import org.lain.engine.util.has

@Serializable
data class Count(var value: Int) : Component

val EngineItem.count
    get() = this.get<Count>()?.value ?: 1

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