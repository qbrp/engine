package org.lain.engine.mc

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
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
    private val worldMap: ConcurrentHashMap<WorldId, Level> = ConcurrentHashMap()
    val client = Entity2PlayerTable<Player>()
    val server = Entity2PlayerTable<ServerPlayer>()

    fun getGeneralPlayer(entity: Player): EnginePlayer? {
        return if (entity is ServerPlayer) {
            server.getPlayer(entity)
        } else {
            client.getPlayer(entity)
        }
    }

    class Entity2PlayerTable<T : Player> {
        private val playerToEntityMap: ConcurrentHashMap<PlayerId, T> = ConcurrentHashMap()
        private val entityToPlayerMap: ConcurrentHashMap<T, EnginePlayer> = ConcurrentHashMap()

        fun setEntity(entity: T, player: PlayerId) {
            playerToEntityMap[player] = entity
        }

        fun setPlayer(entity: T, player: EnginePlayer) {
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

        fun getEntity(playerId: PlayerId): Player? {
            return playerToEntityMap[playerId]
        }

        fun getEntity(player: EnginePlayer): Player? {
            return getEntity(player.id)
        }

        fun requirePlayer(entity: T): EnginePlayer {
            return getPlayer(entity) ?: error("EnginePlayer ${entity.uuid} not found")
        }

        fun getPlayer(entity: T): EnginePlayer? {
            return entityToPlayerMap[entity]
        }

        fun invalidate() {
            playerToEntityMap.clear()
            entityToPlayerMap.clear()
        }
    }

    fun setWorld(id: WorldId, world: Level) {
        worldMap[id] = world
    }

    fun getMcWorld(id: WorldId): Level? {
        return worldMap[id]
    }
}


typealias ServerPlayerTable = EntityTable.Entity2PlayerTable<ServerPlayer>

typealias ClientPlayerTable = EntityTable.Entity2PlayerTable<Player>