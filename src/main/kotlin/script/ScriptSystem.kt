package org.lain.engine.script

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.world.World

@JvmInline
value class ScriptSystemId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toScriptSystemId() = ScriptSystemId(this)

data class SystemPhase(
    val name: String,
    val systems: List<ScriptSystem>,
    val phases: List<SystemPhase>
)

enum class SystemSide {
    SERVER, CLIENT, BOTH
}

data class ScriptSystem(
    val query: List<ScriptComponentType>,
    val side: SystemSide,
    val entityHandleScript: Script<ScriptContext.SystemEntityHandle, *>,
)

fun ScriptSystem.tick(world: World) {
    if (side == SystemSide.CLIENT && !world.isClient) return
    if (side == SystemSide.SERVER && world.isClient) return
    var handle: MutableEntityHandle? = null
    world.componentManager.iterate(query) { mutableComponentsCollection, entity ->
        if (handle == null) {
            handle = MutableEntityHandle(world, entity, mutableComponentsCollection)
        } else {
            handle.entity = entity
            handle.world = world
        }
        entityHandleScript.execute(handle)
    }
}

class MutableEntityHandle(
    override var world: World,
    override var entity: EntityId,
    override val components: Collection<ScriptComponent>
) : ScriptContext.SystemEntityHandle

class ScriptSystemDispatcher {
    private val phases = mutableListOf<SystemPhase>()

    fun load(phases: List<SystemPhase>) {
        this.phases.clear()
        this.phases.addAll(phases)
    }

    fun tick(world: World) {
        phases.forEach { phase -> phase.tick(world) }
    }

    private fun SystemPhase.tick(world: World) {
        systems.forEach { system ->
            system.tick(world)
        }
        phases.forEach { phase ->
            phase.tick(world)
        }
    }
}