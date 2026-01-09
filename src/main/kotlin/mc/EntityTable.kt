package org.lain.engine.mc

import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.world.World as McWorld
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.get
import kotlin.collections.remove

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
    private val worldMap: ConcurrentHashMap<WorldId, McWorld> = ConcurrentHashMap()
    val client = Entity2PlayerTable<PlayerEntity>()
    val server = Entity2PlayerTable<ServerPlayerEntity>()

    fun getGeneralPlayer(entity: PlayerEntity): Player? {
        return client.getPlayer(entity) ?: (entity as? ServerPlayerEntity)?.let { server.getPlayer(it) }
    }

    class Entity2PlayerTable<T : PlayerEntity> {
        private val playerToEntityMap: ConcurrentHashMap<PlayerId, T> = ConcurrentHashMap()
        private val entityToPlayerMap: ConcurrentHashMap<T, Player> = ConcurrentHashMap()

        fun setPlayer(entity: T, player: Player) {
            entityToPlayerMap[entity] = player
            playerToEntityMap[player.id] = entity
        }

        fun removePlayer(entity: T) {
            entityToPlayerMap.remove(entity)?.let {
                playerToEntityMap.remove(it.id)
            }
        }

        fun removePlayer(playerId: PlayerId) {
            playerToEntityMap.remove(playerId)?.let {
                entityToPlayerMap.remove(it)
            }
        }

        fun getEntity(playerId: PlayerId): PlayerEntity? {
            return playerToEntityMap[playerId]
        }

        fun getEntity(player: Player): PlayerEntity? {
            return getEntity(player.id)
        }

        fun requirePlayer(entity: T): Player {
            return getPlayer(entity) ?: error("Player ${entity.uuid} not found")
        }

        fun getPlayer(entity: T): Player? {
            return entityToPlayerMap[entity]
        }

        fun invalidate() {
            playerToEntityMap.clear()
            entityToPlayerMap.clear()
        }
    }

    fun setWorld(id: WorldId, world: McWorld) {
        worldMap[id] = world
    }

    fun getMcWorld(id: WorldId): McWorld? {
        return worldMap[id]
    }
}


typealias ServerPlayerTable = EntityTable.Entity2PlayerTable<ServerPlayerEntity>

typealias ClientPlayerTable = EntityTable.Entity2PlayerTable<PlayerEntity>