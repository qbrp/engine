package org.lain.engine.client.handler

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.iterate
import org.lain.engine.data.ComponentReviveSettings
import org.lain.engine.data.EntityResolver
import org.lain.engine.data.PersistentId
import org.lain.engine.data.revive
import org.lain.engine.item.EngineItem
import org.lain.engine.player.*
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.server.replication.ReplicationSnapshot
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.location
import java.util.*

/**
 * Объект находится за пределами видимости игрока и не синхронизируется точно.
 * Данных о точном положении и жизненном состоянии нет.
 */
data class LowDetail(var enabled: Boolean = false) : Component

var EnginePlayer.isLowDetailed: Boolean
    get() = this.getOrSet { LowDetail() }.enabled
    set(value) {
        this.getOrSet { LowDetail() }.enabled = value
    }

// Координаты объекта не важны, так как он не участвует в игре
private val LOD_POS = Vec3(0, 0, 0)

fun lowDetailedClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: GeneralPlayerData
): EnginePlayer {
    return with(world) {
        commonPlayerInstance(
            PlayerInstantiateSettings(
                world,
                LOD_POS,
                data.displayName,
                developerModeStatus = DeveloperModeStatus(),
                equipment = emptyMap()
            ),
            id,
            character = null,
            look = null,
        ).also {
            it.set(LowDetail(true))
        }
    }
}

fun mainClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: ServerPlayerData,
    developerModeStatus: DeveloperModeStatus,
): EnginePlayer {
    return with(world) {
        commonPlayerInstance(
            PlayerInstantiateSettings(
                world,
                LOD_POS,
                data.general.displayName,
                PlayerModeComponent(),
                MovementStatus(
                    intention = data.speedIntention,
                    stamina = data.stamina
                ),
                data.attributes,
                developerModeStatus = developerModeStatus,
                skinEyeY = data.skinEyeY,
                equipment = emptyMap() // получается как ReplicationSnapshot
            ),
            id,
            character = data.character,
            look = data.look
        ).also { it.isLowDetailed = false }
    }
}

fun World.tickPlayerLowDetailedSystem(
    mainPlayer: EnginePlayer,
    syncRadius: Int
) {
    val syncRadiusSqr = syncRadius * syncRadius
    iterate<PlayerComponent, Location, LowDetail>() { _, _, location, lowDetailed ->
        if (location.position.squaredDistanceTo(mainPlayer.location.position) > syncRadiusSqr) {
            lowDetailed.enabled = true
            return@iterate
        } else {
            lowDetailed.enabled = false
        }
    }
}

fun ReplicationSnapshot.revive(resolver: EntityResolver, settings: ComponentReviveSettings): Component? {
    return when (this) {
        is ReplicationSnapshot.EntityRpcReceiver -> EntityRpcQueue(LinkedList())
        is ReplicationSnapshot.Equipment -> Equipment(
            slots.mapValues { (slot, persistentId) ->
                resolver.find(persistentId) ?: error("Не удалось разрешить ссылку на экипируемый предмет $persistentId (слот $slot)")
            }
                .toMutableMap()
        )

        is ReplicationSnapshot.Component -> component.revive(resolver, settings)
    }
}