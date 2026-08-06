package org.lain.engine.script

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.script.lua.LuaDataStorage
import org.lain.engine.world.World

@JvmInline
value class ScriptSystemId(val value: String) {
    override fun toString(): String = value
}

data class SystemPhase(
    val id: String,
    val systems: List<ScriptSystemId>,
)

enum class SystemSide {
    SERVER, CLIENT, BOTH
}

data class ScriptSystemDefinition(
    val query: List<ScriptComponentType>,
    val side: SystemSide,
    val entityHandleScript: VoidScript<ScriptContext.SystemEntityHandle>,
)

class ScriptSystem(
    val query: List<ScriptComponentType>,
    val entityHandleScript: VoidScript<ScriptContext.SystemEntityHandle>,
    val side: SystemSide
) {
    fun tick(world: World) {
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
}

class ScriptSystemDispatcher {
    private val phases = mutableListOf<PhaseInstructions>()

    fun load(phases: List<SystemPhase>, contents: Contents) {
        this.phases.clear()
        this.phases.addAll(
            phases.map { phase ->
                PhaseInstructions(
                    phase.systems.map {
                        val system = contents.systems[it] ?: error("Missing system $it")
                        ScriptSystem(
                            system.query,
                            system.entityHandleScript,
                            system.side
                        )
                    }
                )
            }
        )
    }

    fun tick(world: World) {
        phases.forEach { phase ->
            phase.systems.forEach { system ->
                system.tick(world)
            }
        }
    }

    data class PhaseInstructions(val systems: List<ScriptSystem>)
}