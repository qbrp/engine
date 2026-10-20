package org.lain.engine.server

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.BackendCharacter
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.data.SerializedInventory
import org.lain.engine.player.character.CharacterChangeRequest
import org.lain.engine.player.character.EngineBackend
import org.lain.engine.world.World

interface ServerPlatform {
    val backend: EngineBackend

    fun beforePlayerInstantiation(player: EnginePlayer) {}
    fun onCompiled(contents: NamespacedStorage) {}
    fun onCharacterApplied(player: EnginePlayer, character: BackendCharacter) {}
    fun serializeInventory(player: EnginePlayer): SerializedInventory = SerializedInventory()
    fun clearInventory(player: EnginePlayer) {}
    fun openInventory(player: EnginePlayer, inventory: SerializedInventory) {}
    fun hasPermission(player: EnginePlayer, permission: String): Boolean = true

    fun World.tickDataPreparation() {}
    fun World.tickSaveSystem() {}
    fun World.tickDataApply() {}

    companion object {
        val DUMMY = object : ServerPlatform {
            override val backend: EngineBackend
                get() = object : EngineBackend {
                    override suspend fun validateCharacterChange(
                        player: EnginePlayer,
                        request: CharacterChangeRequest
                    ): BackendCharacter = TODO()
                }
        }
    }
}