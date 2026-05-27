package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.player.*
import org.lain.engine.world.World

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
) : Component

const val WRITEABLE_OPEN_SOUND = "writable_open"

val WRITEABLE_OPEN_VERB = VerbType(
    "writable_open",
    "Открыть для чтения"
)

context(world: World)
fun appendWriteableVerbs(player: EnginePlayer) {
    player.handle<VerbLookup> {
        if (!(handItem?.hasComponent<Writable>() ?: false)) return@handle
        forAction<InputAction.Base>(WRITEABLE_OPEN_VERB)
    }
}

context(world: World, interaction: InteractionComponent)
fun handleWriteableInteractions(player: EnginePlayer) {
    val handItem = player.handItem ?: return
    player.handleInteraction(WRITEABLE_OPEN_VERB) {
        emitItemInteractionSoundEvent(handItem, WRITEABLE_OPEN_SOUND, player=player)
        player.set(BookOpen(handItem.requireComponent()))
        complete()
    }
}

data class BookOpen(val writeable: Writable) : Component