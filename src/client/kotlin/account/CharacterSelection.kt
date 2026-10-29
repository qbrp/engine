package org.lain.engine.client.account

import kotlinx.coroutines.CompletableDeferred
import org.lain.engine.client.EngineClient
import org.lain.engine.client.render.ui.character.CharacterSelectionScreen
import org.lain.engine.client.render.ui.character.LookSelectionScreen
import org.lain.engine.player.account.AccountResponse
import org.lain.engine.player.character.BackendCharacterData
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.BackendCharacterId
import org.lain.engine.player.character.CharacterSource
import org.lain.engine.player.character.Look
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.player.character.ServerboundSelectedCharacter
import org.lain.engine.util.file.FileSystem

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
        characters: List<Variant>,
    ): Selection? {
        return if (current != null) {
            LookSelectionScreen.awaitGeneralSelection(
                this,
                Variant(current.data, current.source),
                current.look,
                characters,
            )
        } else {
            awaitCharacterSelection(null, characters)
        }
    }

    suspend fun awaitCharacterSelection(
        current: Variant?,
        characters: List<Variant>,
    ) = CharacterSelectionScreen.awaitCharacterSelection(this, current, characters)

    data class CurrentCharacter(
        val data: CharacterData,
        val source: CharacterSource,
        val look: Look,
    )

    sealed interface Selection {
        data class Character(val variant: Variant) : Selection {
            suspend fun toServerboundDto(client: EngineClient, isMultiPlayer: Boolean): ServerboundSelectedCharacter {
                val (character, source) = variant
                val accountManager = client.accountManager

                return if (!isMultiPlayer || source is CharacterSource.Local) {
                    ServerboundSelectedCharacter.Provided(source, character)
                } else {
                    when(source) {
                        is CharacterSource.Backend -> {
                            val account = accountManager.getAuthorized() ?: throw NotAuthorizedException()
                            val ticketDto = account.getSessionTicket()
                            ServerboundSelectedCharacter.ResolveByTicket(source.id, ticketDto)
                        }
                        is CharacterSource.Server -> {
                            ServerboundSelectedCharacter.ResolveServer(source.id)
                        }
                    }
                }
            }
        }
        data class Look(val look: org.lain.engine.player.character.Look) : Selection
    }

    data class Variant(
        val character: CharacterData,
        val source: CharacterSource,
    )

    companion object {
        fun variants(
            backendCharacters: Map<BackendCharacterId, BackendCharacterData>,
            clientCharacters: Map<String, CharacterData>
        ): Map<CharacterSource, Variant> {
            val backendVariants = backendCharacters.map { (id, character) ->
                val source = CharacterSource.Backend(id)
                source to Variant(character.data, source)
            }.toMap()
            val clientVariants = clientCharacters.map { (filename, characterData) ->
                val source = CharacterSource.Local(filename)
                source to Variant(characterData, source)
            }.toMap()
            return backendVariants + clientVariants
        }

        suspend fun variants(account: AccountResponse): List<Variant> {
            return variants(
                account.characters.associate { it.id to it.map() },
                FileSystem.readCharacters()
            ).values.toList()
        }
    }
}
