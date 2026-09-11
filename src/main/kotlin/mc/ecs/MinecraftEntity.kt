package org.lain.engine.mc.ecs

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.mc.engine
import org.lain.engine.mc.engineId
import org.lain.engine.mc.server.EngineMinecraftServer
import org.lain.engine.world.Location
import org.lain.engine.world.World

data class MinecraftEntity(val entity: Entity) : Component

fun EngineMinecraftServer.linkMinecraftEntity(
    entity: Entity,
): EntityId {
    val level = entity.level()
    val world = engine.simulation.worlds[level.engineId]!!
    return with(world) {
        val e = addEntity()
        e.setComponent(MinecraftEntity(entity))
        e.setComponent(Location(entity.position().engine()))
        e
    }
}

fun World.tickMinecraftEntitySystem() {
    iterate<MinecraftEntity, Location> { e, (entity), location ->
        val pos = entity.position()
        location.position.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
    }
}