package org.lain.engine.client.handler

import org.lain.cyberia.ecs.Component
import org.lain.engine.data.ComponentReviveSettings
import org.lain.engine.data.EntityResolver
import org.lain.engine.data.revive
import org.lain.engine.player.*
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.server.replication.ReplicationSnapshot
import org.lain.engine.transport.packet.DeveloperModeStatus
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.World
import java.util.*

// Координаты объекта не важны, так как он не участвует в игре
private val LOD_POS = Vec3(0, 0, 0)

fun remoteClientPlayerInstance(
    world: World,
    player: OnlinePlayer
): EnginePlayer {
    return with(world) {
        commonPlayerInstance(
            PlayerInstantiateSettings(
                world,
                LOD_POS,
                DisplayName(player.username),
                developerModeStatus = DeveloperModeStatus(),
            ),
            player.id,
        )
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
                data.displayName,
                PlayerModeComponent(),
                MovementStatus(
                    intention = data.speedIntention,
                    stamina = data.stamina
                ),
                data.attributes,
                developerModeStatus = developerModeStatus,
            ),
            id
        )
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