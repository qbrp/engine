package org.lain.engine.mc

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.world.World as McWorld
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.util.injectMinecraftPlayerEntityFlow
import org.lain.engine.util.injectPlayersFlow
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentHashMap

/**
 * # Таблица сущностей
 * Общий класс для логического сервера и клиента.
 * Предоставляет доступ к сущностям Minecraft и наоборот.
 * **Таблица создаётся один раз и используется как и логическим сервером, так и логическим клиентом.**
 *
 * - В одиночной игре таблица показывает игроков выделенного сервера - только серверного главного игрока.
 * - В многопользовательской игре показывает загруженных клиентом игроков, включая главного
 * - В выделенном сервере показывает всех игроков
 *
 * @see org.lain.engine.CommonEngineServerMod
 */
class EntityTable {
    private val enginePlayers by injectPlayersFlow()
    private val minecraftPlayers by injectMinecraftPlayerEntityFlow()

    private val playerToEntityMap: ConcurrentHashMap<PlayerId, PlayerEntity> = ConcurrentHashMap()
    private val entityToPlayerMap: ConcurrentHashMap<PlayerEntity, Player> = ConcurrentHashMap()
    private val worldMap: ConcurrentHashMap<WorldId, McWorld> = ConcurrentHashMap()

    fun setWorld(id: WorldId, world: McWorld) {
        worldMap[id] = world
    }

    fun getMcWorld(id: WorldId): McWorld? {
        return worldMap[id]
    }

    fun removePlayer(entity: PlayerEntity) {
        entityToPlayerMap.remove(entity)?.let {
            playerToEntityMap.remove(it.id)
        }
    }

    fun getPlayer(entity: PlayerEntity): Player? {
        return entityToPlayerMap[entity]
            ?: enginePlayers.get().find { it.id.value == entity.uuid }?.also {
                entityToPlayerMap[entity] = it
            }
    }

    fun requirePlayer(entity: PlayerEntity): Player {
        return getPlayer(entity)!!
    }

    fun getEntity(playerId: PlayerId): PlayerEntity? {
        return playerToEntityMap[playerId]
            ?: minecraftPlayers.get().find { it.uuid == playerId.value }?.also {
                playerToEntityMap[playerId] = it
            }
    }

    fun getEntity(player: Player): PlayerEntity? {
        return getEntity(player.id)
    }

    fun requireEntity(player: Player): PlayerEntity {
        return getEntity(player)!!
    }
}
