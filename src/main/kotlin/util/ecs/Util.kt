package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.exists
import org.lain.engine.world.World

context(world: World)
fun EntityId.assertExists() {
    assert(exists()) { "Entity $this not exists" }
}

context(world: World)
fun EntityId.destroyIfExists() {
    if (exists()) destroy()
}

inline fun <reified T : Component> componentType() = componentTypeOf(T::class)