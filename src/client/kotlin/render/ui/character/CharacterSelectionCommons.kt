package org.lain.engine.client.render.ui.character

import kotlinx.coroutines.CompletableDeferred
import net.minecraft.client.gui.screens.Screen
import org.lain.engine.client.handler.ClientHandler
import org.lain.engine.player.character.EngineCharacter

fun Screen.CharacterApplyConfirmationWaitOverlay(
    character: EngineCharacter?,
    characterCompletableDeferred: CompletableDeferred<EngineCharacter?>,
    handler: ClientHandler,
    requestId: Long? = null
) = CharacterApplyConfirmationWaitOverlay(
    handler.deferCharacterApplyConfirmation(requestId),
    onClose = { onClose() },
    onFaded = {
        //TODO: сделать функцию grabMouse, но не закрывающую текущий экран
        //minecraft.mouseHandler.grabMouse()
        characterCompletableDeferred.complete(character)
    }
)
