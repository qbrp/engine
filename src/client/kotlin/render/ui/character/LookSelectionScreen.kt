package org.lain.engine.client.render.ui.character

import com.mojang.blaze3d.platform.InputConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.input.KeyEvent
import org.lain.engine.client.EngineClient
import org.lain.engine.client.account.SkinTextureManager
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.util.MinecraftClientDispatcher
import org.lain.engine.mc.getText
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.Look
import org.lain.engine.util.nextIdFast

class LookSelectionScreen(
    private val client: EngineClient,
    private val character: EngineCharacter,
    private val characters: List<EngineCharacter>,
    look: Look,
    looks: List<Look>,
    private val handler: ClientHandler = client.handler,
    skinTextureManager: SkinTextureManager = client.skinTextureManager,
) : AbstractSelectionScreen<Look>() {
    private val resultCompletableDeferred: CompletableDeferred<Result?> = CompletableDeferred()
    private val selectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val looksWheel: LooksWheel<Look> = run {
        val entries = looks.map {
            LooksWheel.Entry(
                it,
                character.profile.name.copy(name = it.title).gradientChars.getText(),
                character.profile,
                it
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

    suspend fun awaitResult() = resultCompletableDeferred.await()

    override fun onClose() {
        super.onClose()
        selectionScope.cancel()
        if (!resultCompletableDeferred.isCompleted) {
            resultCompletableDeferred.complete(null)
        }
    }

    override fun onEntrySelected(selected: LooksWheel.Entry<Look>) {
        if (overlay == null) {
            val requestId = nextIdFast()
            overlay = CharacterApplyConfirmationWaitOverlay(
                handler.deferCharacterApplyConfirmation(requestId),
                onClose = { onClose() },
                onFaded = {
                    resultCompletableDeferred.complete(Result.SelectedLook(selected.look, requestId))
                }
            )
        }
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.key == InputConstants.KEY_O) {
            switchToCharacterSelection()
            true
        } else {
            super.keyPressed(keyEvent)
        }
    }

    private fun switchToCharacterSelection() {
        selectionScope.launch {
            val requestId = nextIdFast()
            resultCompletableDeferred.complete(
                CharacterSelectionScreen.awaitCharacterSelection(client, character, characters, requestId)
                    ?.let { Result.SelectedCharacter(it, requestId) }
            )
        }
    }

    sealed class Result {
        data class SelectedCharacter(val character: EngineCharacter, val requestId: Long) : Result()
        data class SelectedLook(val look: Look, val requestId: Long) : Result()
    }

    companion object {
        /**
         * @return null если экран выбора образов был закрыт
         */
        suspend fun awaitGeneralSelection(
            client: EngineClient,
            character: EngineCharacter,
            characters: List<EngineCharacter>,
            look: Look
        ): Result? {
            if (character.looks.size == 1) {
                val requestId = nextIdFast()
                return CharacterSelectionScreen.awaitCharacterSelection(client, character, characters, requestId)
                    ?.let { Result.SelectedCharacter(it, requestId) }
            }

            val screen = withContext(MinecraftClientDispatcher) {
                val screen = LookSelectionScreen(client, character, characters, look, character.looks)
                MinecraftClient.setScreen(screen)
                screen
            }
            return screen.awaitResult()
        }
    }
}
