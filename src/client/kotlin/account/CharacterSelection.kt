package org.lain.engine.client.account

import kotlinx.coroutines.CompletableDeferred
import org.lain.engine.client.EngineClient
import org.lain.engine.client.render.ui.character.CharacterSelectionScreen
import org.lain.engine.client.render.ui.character.LookSelectionScreen
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.Look

class CharacterSelection(
    val client: EngineClient,
) {
    private val confirmation = CompletableDeferred<Unit>()

    internal suspend fun awaitConfirmation() {
        confirmation.await()
    }

    fun confirm(): Boolean = confirmation.complete(Unit)

    fun fail(errorMessage: String): Boolean =
        confirmation.completeExceptionally(RuntimeException(errorMessage))

    fun cancel() {
        confirmation.cancel()
    }

    suspend fun awaitSelection(
        current: CurrentCharacter?,
        characters: List<EngineCharacter>,
    ): Selection? {
        return if (current != null) {
            LookSelectionScreen.awaitGeneralSelection(
                this,
                current.character,
                current.look,
                characters,
            )
        } else {
            CharacterSelectionScreen.awaitCharacterSelection(
                this,
                null,
                characters,
            )
        }
    }

    data class CurrentCharacter(
        val character: EngineCharacter,
        val look: Look,
    )

    sealed interface Selection {
        data class Character(val character: EngineCharacter) : Selection
        data class Look(val look: org.lain.engine.player.character.Look) : Selection
    }
}
