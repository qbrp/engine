package org.lain.engine.client.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.lain.engine.client.GameSession
import org.lain.engine.client.util.withClientContext
import org.lain.engine.util.nextIdFast

class CharacterChange(
    private val gameSession: GameSession,
    private val currentCharacter: CharacterSelection.CurrentCharacter?,
) {
    private val client = gameSession.client
    private val handler = client.handler
    private val requestId = nextIdFast()
    private val selection = CharacterSelection(client)
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
        val account = accountManager.getAccountResponseUpdateLaunching()
        val characters = account.characters.map { it.map() }
        val selected = selection.awaitSelection(currentCharacter, characters) ?: return

        when (selected) {
            is CharacterSelection.Selection.Character -> {
                if (gameSession.isMultiPlayer) {
                    accountManager.sessionTicketOperation(
                        accountManager.getAuthorized() ?: throw NotAuthorizedException()
                    ) { sessionTicket ->
                        withClientContext {
                            handler.onCharacterSelectedMultiplayer(
                                selected.character,
                                sessionTicket,
                                requestId,
                            )
                        }
                        selection.awaitConfirmation()
                    }
                } else {
                    withClientContext {
                        handler.onCharacterSelectedSingleplayer(
                            selected.character,
                            requestId,
                        )
                    }
                    selection.awaitConfirmation()
                }
            }

            is CharacterSelection.Selection.Look -> {
                withClientContext {
                    handler.onLookSelected(selected.look.id, requestId)
                }
                selection.awaitConfirmation()
            }
        }
    }
}
