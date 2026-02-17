package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.Component

@Serializable
/**
 * # Книги, записываемые предметы
 * Позволяют через ванильный игровой интерфейс записывать в себя любой контент.
 * **Содержимое книг бекапится и фиксируется в отдельной папке.**
 * @param pages Доступное количество страниц (т.е. их максимальное число)
 * @param contents Текст на страницах
 * @param backgroundAsset Идентификатор ассета, используемый для рисования фона
 */
data class Writable(
    val pages: Int,
    var contents: List<String>,
    val backgroundAsset: String? = null,
) : ItemComponent

const val WRITEABLE_OPEN_SOUND = "writable_open"

object OpenBookTag : Component