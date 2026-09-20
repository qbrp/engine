package org.lain.engine.server

import org.lain.engine.server.account.SessionTicket
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.data.SerializedInventory
import org.lain.engine.player.character.CharacterId
import org.lain.engine.world.World

interface ServerPlatform {
    context(world: World)
    fun onPlayerInstantiated(player: EnginePlayer) {}
    fun onCompiled(contents: NamespacedStorage) {}
    fun onCharacterApplied(player: EnginePlayer, character: EngineCharacter) {}
    suspend fun validateCharacter(
        player: EnginePlayer,
        characterId: CharacterId,
        character: EngineCharacter?,
        sessionTicket: SessionTicket?
    ): EngineCharacter = character!!
    fun serializeInventory(player: EnginePlayer): SerializedInventory = SerializedInventory()
    fun clearInventory(player: EnginePlayer) {}
    fun openInventory(player: EnginePlayer, inventory: SerializedInventory) {}
    fun hasPermission(player: EnginePlayer, permission: String): Boolean = true

    fun World.prepareData() {}
    fun World.updateBulletHitSystem() {}
    fun World.updateSaveSystem() {}
    fun World.applyData() {}

    companion object {
        val DUMMY = object : ServerPlatform {}
    }
}