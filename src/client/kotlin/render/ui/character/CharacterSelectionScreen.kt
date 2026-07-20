package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import org.lain.engine.client.EngineClient
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.EngineCharacter

class CharacterSelectionScreen(
    character: EngineCharacter?,
    characters: List<EngineCharacter>,
    skinTextureManager: SkinTextureManager,
    private val handler: ClientHandler,
) : AbstractSelectionScreen<EngineCharacter>() {
    private val characterCompletableDeferred: CompletableDeferred<EngineCharacter?> = CompletableDeferred()
    override val looksWheel: LooksWheel<EngineCharacter> = run {
        val entries = characters.map { LooksWheel.Entry(it.baseLook, it.profile.name.gradientChars.getText(), it.profile, it) }
        val initial = entries.find { it.profile.id == character?.profile?.id }
        LooksWheel(
            skinTextureManager,
            initial,
            entries,
            onSelectLook = ::selectEntry
        )
    }

    override fun onClose() {
        super.onClose()
        if (!characterCompletableDeferred.isCompleted) {
            characterCompletableDeferred.complete(null)
        }
    }

    suspend fun awaitSelection() = characterCompletableDeferred.await()

    override fun onEntrySelected(selected: LooksWheel.Entry<EngineCharacter>) {
        overlay = CharacterApplyConfirmationWaitOverlay(selected.data, characterCompletableDeferred, handler)
    }

    companion object {
        /**
         * @return null если экран выбора персонажей был закрыт
         */
        suspend fun awaitCharacterSelection(
            client: EngineClient,
            character: EngineCharacter?,
            characters: List<EngineCharacter>
        ): EngineCharacter? {
            val screen = withContext(MinecraftClientDispatcher) {
                val screen =
                    CharacterSelectionScreen(character, characters, client.skinTextureManager, client.handler)
                MinecraftClient.setScreen(screen)
                screen
            }
            return screen.awaitSelection()
        }
    }
}
