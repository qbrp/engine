package org.lain.engine.client.account

import kotlinx.coroutines.*
import org.lain.engine.client.GameSession
import org.lain.engine.client.handler.LocalCharacter
import org.lain.engine.client.util.withClientContext
import org.lain.engine.player.character.ServerboundSelectedCharacter
import org.lain.engine.server.account.SessionTicket
import org.lain.engine.util.nextIdFast

class CharacterChange(
    private val gameSession: GameSession,
    private val currentCharacter: CharacterSelection.CurrentCharacter?,
) {
    private val client = gameSession.client
    private val serverPlayState = gameSession.serverPlayState
    private val handler = client.handler
    private val requestId = nextIdFast()
    private val selection = CharacterSelection(
        client,
        gameSession.namespacedStorage,
        serverPlayState
    )
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    fun start(): Job {
        check(job == null) { "Character change has already started" }
        return scope.launch {
            try {
                execute()
            } finally {
                selection.cancel()
            }
        }.also { job = it }
    }

    fun confirm(requestId: Long, errorMessage: String?): Boolean {
        if (requestId != this.requestId) return false
        return if (errorMessage == null) {
            selection.confirm()
        } else {
            selection.fail(errorMessage)
        }
    }

    fun cancel() {
        selection.cancel()
        scope.cancel()
    }

    private suspend fun execute() {
        val accountManager = client.accountManager
        val account = accountManager.requireAccountResponse()

        val variants = CharacterSelection.variantsList(account)
        val selected = selection.awaitSelection(currentCharacter, variants) ?: return

        when (selected) {
            is CharacterSelection.Selection.Character -> {
                withClientContext {
                    var ticket: SessionTicket? = null
                    try {
                        val request = selected.toServerboundDto(client, gameSession.isMultiPlayer)

                        if (request is ServerboundSelectedCharacter.ResolveByTicket) {
                            ticket = request.ticket.map()
                        }

                        handler.requestCharacterSelection(request, requestId)
                        selection.awaitConfirmation()

                        gameSession.character = LocalCharacter(selected.variant.source)
                    } finally {
                        ticket?.let {
                            accountManager.requireAuthorized().revokeSessionTicket(ticket.hash)
                        }
                    }
                }
            }

            is CharacterSelection.Selection.Look -> withClientContext {
                handler.onLookSelected(selected.look.id, requestId)
                selection.awaitConfirmation()
            }
        }
    }
}
