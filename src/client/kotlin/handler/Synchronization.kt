package org.lain.engine.client.handler

import org.lain.engine.client.util.registerComponentsClient
import org.lain.engine.item.Count
import org.lain.engine.item.EngineItem
import org.lain.engine.item.itemInstance
import org.lain.engine.player.*
import org.lain.engine.transport.packet.*
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.ComponentState
import org.lain.engine.util.component.getOrSet
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
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
            data.displayName,
            MovementStatus(
                intention = data.speedIntention,
                stamina = data.stamina
            ),
            data.attributes,
            developerModeStatus = developerModeStatus
        ),
        id
    ).also { it.isLowDetailed = false }
}

fun clientItem(world: World, item: ClientboundItemData): EngineItem {
    val state = ComponentState(item.components)
    return itemInstance(
        item.uuid,
        item.id,
        Location(world, item.pos),
        Count(item.count, item.maxCount),
        state
    )
}

fun clientWorld(data: ClientboundWorldData) = World(data.id).apply {
    componentManager.registerComponentsClient()
}