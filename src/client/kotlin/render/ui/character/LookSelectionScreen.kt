package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.withContext
import net.minecraft.client.input.KeyEvent
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.Look

class LookSelectionScreen(
    characterSelection: CharacterSelection,
    character: EngineCharacter,
    look: Look,
    skinTextureManager: SkinTextureManager = characterSelection.client.skinTextureManager,
    looks: List<Look> = character.looks,
) : AbstractSelectionScreen<LookSelectionScreen.Result>(characterSelection) {

    sealed interface Result {
        data class Selected(val look: Look) : Result
        data object Switch : Result
    }

    override val looksWheel: LooksWheel<Result> = run {
        val profile = character.profile
        val entries: List<LooksWheel.Entry<Result>> = looks.map {
            LooksWheel.Entry(
                it,
                profile.name.copy(name = it.title).gradientChars.getText(),
                profile,
                Result.Selected(it)
            )
        }
        val initial = entries.find { it.look.id == look.id }
        LooksWheel(
            skinTextureManager,
            initial,
            entries,
            onSelectLook = ::selectEntry
        )
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.key == InputConstants.KEY_O && overlay == null) {
            selection.complete(Result.Switch) // переключение в выбор персонажей
            true
        } else {
            super.keyPressed(keyEvent)
        }
    }

    companion object {
        /**
         * @return null если экран выбора образов был закрыт
         */
        suspend fun awaitGeneralSelection(
            characterSelection: CharacterSelection,
            character: EngineCharacter,
            look: Look,
            characters: List<EngineCharacter>,
        ): CharacterSelection.Selection? {
            if (character.looks.size == 1) {
                return CharacterSelectionScreen.awaitCharacterSelection(characterSelection, character, characters)
            }

            val screen = withContext(MinecraftClientDispatcher) {
                val screen = LookSelectionScreen(characterSelection, character, look)
                MinecraftClient.setScreen(screen)
                screen
            }
            return when (val result = screen.selection.await()) {
                null -> null
                Result.Switch -> CharacterSelectionScreen.awaitCharacterSelection(
                    characterSelection,
                    character,
                    characters,
                )
                is Result.Selected -> CharacterSelection.Selection.Look(result.look)
            }
        }
    }
}
