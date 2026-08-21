package org.lain.engine.container

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.*
import org.lain.engine.server.Networked
import org.lain.engine.storage.PersistentId
import org.lain.engine.storage.PersistentIdComponent
import org.lain.engine.util.component.ComponentState
import org.lain.engine.world.Location
import org.lain.engine.world.World
import kotlin.let

@Serializable
object Container : Component

@JvmInline
value class ContainerEntity(val entity: EntityId) {
    context(world: World)
    fun destroy() = entity.destroy()
}

internal fun EntityId.castContainer() = ContainerEntity(this)

/**
 * ## Компоненты предметов
 */
data class HasContainer(val container: EntityId) : Component

fun WriteComponentAccess.createContainer(
    location: Location,
    componentState: ComponentState? = null,
    persistentId: PersistentId? = null,
    networked: Boolean = false
): ContainerEntity {
    return addEntity {
        if (networked) setComponent(Networked)
        componentState?.let { copyState(it) }
        persistentId?.let { setComponent(PersistentIdComponent(it)) }
        setComponent(location)
        setComponent(Entries(mutableSetOf()))
        setComponent(Container)
    }.castContainer()
}