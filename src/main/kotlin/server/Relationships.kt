package org.lain.engine.server

import org.lain.cyberia.ecs.Component
import org.lain.engine.util.component.EntityId

data class Parent(val entity: EntityId) : Component

data class Children(val entities: MutableSet<EntityId>) : Component