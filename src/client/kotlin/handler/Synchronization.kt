package org.lain.engine.client.handler

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.componentTypeOfGeneral
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.player.*
import org.lain.engine.player.interaction.Action
import org.lain.engine.player.interaction.ActionSyncEvent
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.GeneralPlayerData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.EngineLogger
import org.lain.engine.util.Log
import org.lain.engine.util.LogLevel
import org.lain.engine.util.LogMessages
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
    return with(world) {
        commonPlayerInstance(
            PlayerInstantiateSettings(
                world,
                LOD_POS,
                data.displayName,
                developerModeStatus = DeveloperModeStatus()
            ),
            id
        ).also {
            it.isLowDetailed = true
        }
    }
}

fun mainClientPlayerInstance(
    id: PlayerId,
    world: World,
    data: ServerPlayerData,
    developerModeStatus: DeveloperModeStatus
): EnginePlayer {
    return with(world) {
        commonPlayerInstance(
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
}

fun World.tickActionSyncSystem(handler: ClientHandler) {
    iterate<ActionSyncEvent> { _, event ->
        val identity = ClientHandler.InteractionIdentity(event.tick, event.entity)
        if (identity !in handler.processedInteraction) {
            event.entity.setComponent(event.action, componentTypeOfGeneral(event.action) as ComponentType<Action>)
        } else {
            EngineLogger.log(
                Log(
                    LogMessages.INTERACTION_SKIP,
                    LogLevel.INFO,
                    world = id,
                    tick = ticks.toULong(),
                    data = mapOf(
                        "interaction_tick" to event.tick.toString()
                    )
                )
            )
        }
    }
}

fun World.tickProcessedActions(handler: ClientHandler) = iterate<ActionSyncEvent> { _, event ->
    handler.processedInteraction += ClientHandler.InteractionIdentity(event.tick, event.entity)
}
