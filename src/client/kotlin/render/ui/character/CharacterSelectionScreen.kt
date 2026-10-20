package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.withContext
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.BackendCharacter

class CharacterSelectionScreen(
    character: BackendCharacter?,
    characters: List<BackendCharacter>,
    characterSelection: CharacterSelection,
) : AbstractSelectionScreen<CharacterSelection.Selection.Character>(characterSelection) {
    override val looksWheel: LooksWheel<CharacterSelection.Selection.Character> = run {
        val entries = characters.map {
            LooksWheel.Entry(
                it.baseLook,
                it.profile.name.gradientChars.getText(),
                it.profile,
                CharacterSelection.Selection.Character(it),
                it.profile.id.toString()
            )
        }
        val initial = entries.find { it.profile.id == character?.profile?.id }
        LooksWheel(
            characterSelection.client.skinTextureManager,
            initial,
            entries,
            onSelectLook = ::selectEntry
        )
    }

    companion object {
        /**
         * @return null если экран выбора персонажей был закрыт
         */
        suspend fun awaitCharacterSelection(
            characterSelection: CharacterSelection,
            character: BackendCharacter?,
            characters: List<BackendCharacter>,
        ): CharacterSelection.Selection.Character? {
            val screen = withContext(MinecraftClientDispatcher) {
                val screen =
                    CharacterSelectionScreen(character, characters, characterSelection)
                MinecraftClient.setScreen(screen)
                screen
            }
            return screen.selection.await()
        }
    }
}
