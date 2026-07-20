package org.lain.engine.client.handler

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lain.engine.client.EngineClient
import org.lain.engine.client.account.NotAuthorizedException
import org.lain.engine.client.handler.ClientHandler.Companion.LOGGER
import org.lain.engine.client.render.ui.character.CharacterSelectionScreen
import org.lain.engine.client.util.withClientContext
import org.lain.engine.mc.commands.friendlyError
import org.lain.engine.script.NamespaceHashMapValidationResult
import org.lain.engine.script.validateNamespaceHashMap
import org.lain.engine.transport.packet.GeneralServerData
import org.lain.engine.transport.packet.VerificationDataPacket

class MultiplayerAuthorization(
    modIds: List<String>,
    private val client: EngineClient,
    private val handler: ClientHandler
) {
    var verificationStateStartCompletableDeferred: CompletableDeferred<VerificationDataPacket>? = null
        private set

    @Volatile
    var state: State = State.AUTHORIZATION
        private set

    val canCloseLevelLoadingScreen
        get() = state == State.DONE

    private val job = CoroutineScope(Dispatchers.IO).launch {
        try {
            val accountManager = client.accountManager
            val authorized = accountManager.getAuthorized() ?: throw NotAuthorizedException()
            val account = authorized.getAccount()

            accountManager.sessionTicketOperation(authorized) { sessionTicket ->
                handler.sendAuthPacket(modIds, sessionTicket)

                val server = awaitVerificationState()

                state = State.COMPILATION
                val namespaceHashMap = withClientContext {
                    client.createLuaContext(server.serverId)
                    client.compileScripts()

                    val namespaceHashMap = client.namespacedStorage.get().namespaceHashMap
                    if (server.requireIdenticalNamespaces) {
                        val result = validateNamespaceHashMap(namespaceHashMap, server.namespaceHashMap)
                        if (result is NamespaceHashMapValidationResult.Error) {
                            friendlyError(result.computeErrorMessage())
                        }
                    }
                    namespaceHashMap
                }

                val selectedCharacter = withClientContext {
                    CharacterSelectionScreen.awaitCharacterSelection(
                        client,
                        null,
                        account.characters.map { it.map() }
                    )
                }
                state = State.DONE

                handler.sendVerificationPacket(namespaceHashMap, selectedCharacter)
                handler.awaitJoinGamePacket()
            }
        } catch (e: CancellationException) {
            LOGGER.info("Отменена корутина входа на сервер")
            throw e
        } catch (e: Exception) {
            client.execute { client.eventListener.disconnect(e.message ?: "Непредвиденная ошибка") }
            e.printStackTrace()
        }
    }

    suspend fun awaitVerificationState(): GeneralServerData {
        val deferred = CompletableDeferred<VerificationDataPacket>()
        verificationStateStartCompletableDeferred = deferred
        return deferred.await().server
    }

    fun cancel() {
        job.cancel()
    }

    enum class State {
        AUTHORIZATION, COMPILATION, DONE
    }
}