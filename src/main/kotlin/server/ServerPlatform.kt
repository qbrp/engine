package org.lain.engine.server

import org.lain.engine.mc.server.SessionTicket
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.storage.SerializedInventory
import org.lain.engine.world.World

interface ServerPlatform {
    context(world: World)
    fun onPlayerInstantiated(player: EnginePlayer)
    fun onCompiled(contents: NamespacedStorage)
    fun onCharacterApplied(player: EnginePlayer, character: EngineCharacter)
    suspend fun validateCharacter(
        player: EnginePlayer,
        characterId: String,
        character: EngineCharacter?,
        sessionTicket: SessionTicket?
    ): EngineCharacter
    fun serializeInventory(player: EnginePlayer): SerializedInventory
    fun clearInventory(player: EnginePlayer)
    fun openInventory(player: EnginePlayer, inventory: SerializedInventory)
}