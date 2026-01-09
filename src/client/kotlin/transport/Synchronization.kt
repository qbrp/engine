package org.lain.engine.client.transport

import org.lain.engine.player.MovementStatus
import org.lain.engine.player.Player
import org.lain.engine.player.PlayerId
import org.lain.engine.player.PlayerInstantiateSettings
import org.lain.engine.player.commonPlayerInstance
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.Component
import org.lain.engine.util.Vec3
import org.lain.engine.util.getOrSet
import org.lain.engine.world.World

/**
 * Объект находится за пределами видимости игрока и не синхронизируется точно.
 * Данных о точном положении и жизненном состоянии нет.
 */
data class LowDetail(var enabled: Boolean = false) : Component

var Player.isLowDetailed: Boolean
    get() = this.getOrSet { LowDetail() }.enabled
    set(value) { this.getOrSet { LowDetail() }.enabled = value }

// Координаты объекта не важны, так как он не участвует в игре
private val LOD_POS = Vec3(0, 0, 0)

fun lowDetailedClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: GeneralPlayerData
): Player {
    return commonPlayerInstance(
        PlayerInstantiateSettings(
            world,
            LOD_POS,
            data.displayName,
        ),
        id
    ).also { it.isLowDetailed = true }
}

fun mainClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: ServerPlayerData
): Player {
    return commonPlayerInstance(
        PlayerInstantiateSettings(
            world,
            LOD_POS,
            data.displayName,
            MovementStatus(
                intention = data.speedIntention,
                stamina = data.stamina
            ),
            data.attributes
        ),
        id
    ).also { it.isLowDetailed = false }
}

fun clientWorld(data: ClientboundWorldData) = World(data.id)