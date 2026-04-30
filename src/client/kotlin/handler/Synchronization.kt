package org.lain.engine.client.handler

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getOrSet
import org.lain.engine.player.*
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World

/**
 * Объект находится за пределами видимости игрока и не синхронизируется точно.
 * Данных о точном положении и жизненном состоянии нет.
 */
data class LowDetail(var enabled: Boolean = false) : Component

var EnginePlayer.isLowDetailed: Boolean
    get() = this.getOrSet { LowDetail() }.enabled
    set(value) { this.getOrSet { LowDetail() }.enabled = value }

// Координаты объекта не важны, так как он не участвует в игре
private val LOD_POS = Vec3(0, 0, 0)

fun lowDetailedClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: GeneralPlayerData
): EnginePlayer {
    return commonPlayerInstance(
        PlayerInstantiateSettings(
            world,
            LOD_POS,
            data.displayName,
            developerModeStatus = DeveloperModeStatus()
        ),
        id
    ).also { it.isLowDetailed = true }
}

fun mainClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: ServerPlayerData,
    developerModeStatus: DeveloperModeStatus
): EnginePlayer {
    return commonPlayerInstance(
        PlayerInstantiateSettings(
            world,
            LOD_POS,
            data.general.displayName,
            MovementStatus(
                intention = data.speedIntention,
                stamina = data.stamina
            ),
            data.attributes,
            developerModeStatus = developerModeStatus,
            skinEyeY = data.skinEyeY
        ),
        id
    ).also { it.isLowDetailed = false }
}

fun clientWorld(
    thread: Thread,
    data: ClientboundWorldData,
    namespacedStorage: NamespacedStorage,
) = World(data.id, componentManager = ComponentWorld(thread), isClient = true, namespacedStorage = namespacedStorage)