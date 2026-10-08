package org.lain.engine.script

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.world.World

@JvmInline
value class ScriptSystemId(val value: EngineId) : Identifiable {
    override val engineId: EngineId get() = value
    override fun toString(): String = value.toString()
}

fun EngineId.toScriptSystemId() = ScriptSystemId(this)

data class TickPhases(
    val base: SystemPhase = SystemPhase(),
    val verbLookup: SystemPhase = SystemPhase(),
)

data class SystemPhase(
    val name: String = "Root",
    val steps: List<PhaseStep> = emptyList()
) {
    fun tick(world: World) {
        steps.forEach { step ->
            when(step) {
                is PhaseStep.Phase -> step.phase.tick(world)
                is PhaseStep.System -> step.system.tick(world)
            }
        }
    }
}

sealed class PhaseStep {
    data class Phase(val phase: SystemPhase) : PhaseStep()
    data class System(val system: ScriptSystem) : PhaseStep()
}

enum class SystemSide {
    SERVER, CLIENT, BOTH
}

data class ScriptSystem(
    val id: ScriptSystemId,
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
    var phases = TickPhases()
}