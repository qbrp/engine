package org.lain.engine.server

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.ServerCharacterData
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.data.SerializedInventory
import org.lain.engine.player.character.CharacterData
import org.lain.engine.player.character.ServerboundSelectedCharacter
import org.lain.engine.player.character.EngineBackend
import org.lain.engine.world.World

interface ServerPlatform {
    val backend: EngineBackend

    fun beforePlayerInstantiation(player: EnginePlayer) {}
    fun onCompiled(contents: NamespacedStorage) {}
    fun onCharacterApplied(player: EnginePlayer, character: ServerCharacterData) {}
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
                    override suspend fun validateCharacter(
                        canProvideCharacters: Boolean,
                        request: ServerboundSelectedCharacter
                    ): CharacterData = TODO()
                }
        }
    }
}