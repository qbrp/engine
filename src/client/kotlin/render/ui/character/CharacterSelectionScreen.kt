package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.withContext
import org.lain.engine.client.account.CharacterSelection
import org.lain.engine.client.account.CharacterSelection.Variant
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.character.CharacterData
import org.lain.engine.script.NamespacedStorageAccess

class CharacterSelectionScreen(
    val initialVariant: Variant?,
    characters: List<Variant>,
    characterSelection: CharacterSelection,
) : AbstractSelectionScreen<CharacterSelection.Selection.Character>(characterSelection) {
    override val looksWheel: LooksWheel<CharacterSelection.Selection.Character> = run {
        val entries = entries(characters, initialVariant)
        LooksWheel(
            characterSelection.namespacedStorageAccess,
            characterSelection.client.skinTextureManager,
            entries,
            onSelectLook = ::selectEntry
        )
    }

    private fun entries(variants: List<Variant>, initial: Variant?) = variants.map { variant ->
        val isInitial = variant == initial
        val character = variant.character
        LooksWheel.Entry(
            character.baseLook,
            character.profile.name.gradientChars.getText(),
            character.profile,
            CharacterSelection.Selection.Character(variant),
            characterSelection.playState?.characters[variant.source]?.renderState,
            isInitial,
        )
    }

    override suspend fun onReload(account: AccountResponse) {
        looksWheel.reloadItems(entries(CharacterSelection.variantsList(account), initialVariant))
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
                val screen = CharacterSelectionScreen(
                    initialVariant,
                    characters,
                    characterSelection
                )
                MinecraftClient.setScreen(screen)
                screen
            }
            return screen.selection.await()
        }
    }
}
