package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.withContext
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.Look

class LookSelectionScreen(
    characterSelection: CharacterSelection,
    character: CharacterData,
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
                Result.Selected(it),
                it.id == look.id
            )
        }
        LooksWheel(
            skinTextureManager,
            entries,
            onSelectLook = ::selectEntry
        )
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (keyCode == InputConstants.KEY_O && overlay == null) {
            selection.complete(Result.Switch) // переключение в выбор персонажей
            true
        } else {
            super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    companion object {
        suspend fun awaitGeneralSelection(
            characterSelection: CharacterSelection,
            selectedCharacterVariant: CharacterSelection.Variant,
            look: Look,
            variants: List<CharacterSelection.Variant>,
        ): CharacterSelection.Selection? {
            if (selectedCharacterVariant.character.looks.size == 1) {
                return CharacterSelectionScreen.awaitCharacterSelection(
                    characterSelection,
                    selectedCharacterVariant,
                    variants
                )
            }

            val screen = withContext(MinecraftClientDispatcher) {
                val screen = LookSelectionScreen(characterSelection, selectedCharacterVariant.character, look)
                MinecraftClient.setScreen(screen)
                screen
            }
            return when (val result = screen.selection.await()) {
                null -> null
                Result.Switch -> CharacterSelectionScreen.awaitCharacterSelection(
                    characterSelection,
                    selectedCharacterVariant,
                    variants,
                )

                is Result.Selected -> CharacterSelection.Selection.Look(result.look)
            }
        }
    }
}
