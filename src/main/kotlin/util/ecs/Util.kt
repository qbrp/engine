package org.lain.engine.util.ecs

import org.lain.cyberia.ecs.exists
import org.lain.engine.world.World

context(world: World)
fun EntityId.assertExists() {
    assert(exists()) { "Entity $this not exists" }
}