package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.withContext
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.account.CharacterSelection.Variant
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.CharacterData

class CharacterSelectionScreen(
    initialVariant: Variant?,
    characters: List<Variant>,
    characterSelection: CharacterSelection,
) : AbstractSelectionScreen<CharacterSelection.Selection.Character>(characterSelection) {
    override val looksWheel: LooksWheel<CharacterSelection.Selection.Character> = run {
        val entries = characters.map { variant ->
            val isInitial = variant == initialVariant
            val character = variant.character
            LooksWheel.Entry(
                character.baseLook,
                character.profile.name.gradientChars.getText(),
                character.profile,
                CharacterSelection.Selection.Character(variant),
                isInitial,
            )
        }
        LooksWheel(
            characterSelection.client.skinTextureManager,
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
            initialVariant: Variant?,
            characters: List<Variant>,
        ): CharacterSelection.Selection.Character? {
            val screen = withContext(MinecraftClientDispatcher) {
                val screen = CharacterSelectionScreen(initialVariant, characters, characterSelection)
                MinecraftClient.setScreen(screen)
                screen
            }
            return screen.selection.await()
        }
    }
}
