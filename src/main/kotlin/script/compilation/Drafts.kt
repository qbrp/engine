package org.lain.engine.script.compilation

import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.player.interaction.ProgressionAnimation
import org.lain.engine.player.interaction.ProgressionAnimationId
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Identifiable
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.Script
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptId
import org.lain.engine.script.ScriptSystemId
import org.lain.engine.script.SystemSide
import org.lain.engine.script.VoidScript
import org.lain.engine.util.AnyInput
import org.lain.engine.util.OperationActor
import org.lain.engine.util.OperationId
import org.lain.engine.world.SoundEvent
import org.lain.engine.world.SoundEventId

data class NamespaceDraft(
    val items: Map<ItemId, ItemPrefab>,
    val sounds: Map<SoundEventId, SoundEvent>,
    val progressionAnimations: Map<ProgressionAnimationId, ProgressionAnimation>,
    val scripts: Map<ScriptId, Script<*, *>> = mapOf(),
    val components: Map<ScriptComponentId, ScriptComponentType> = mapOf(),
    val operations: Map<OperationId, Operation> = mapOf(),
    val systems: Map<ScriptSystemId, ScriptSystem> = mapOf()
) {
    val holders: List<Map<out Identifiable, Any>> =
        listOf(items, sounds, scripts, progressionAnimations, components, operations, systems)

    data class ScriptSystem(
        val queryComponents: List<ScriptComponentId>,
        val side: SystemSide,
        val entityHandleScript: Script<ScriptContext.SystemEntityHandle, *>,
    )

    data class Operation(
        val name: String,
        val script: ScriptId,
        val inputs: List<AnyInput>,
        val actors: List<OperationActor.Type> = OperationActor.Type.entries,
        val permission: String? = null,
    )
}

data class SystemPhaseDraft(
    val name: String,
    val systems: List<ScriptSystemId>,
    val phases: List<SystemPhaseDraft>
)

data class BuildDraft(
    val namespaces: Map<NamespaceId, NamespaceDraft>,
    val callbacks: Map<CallbackType<*, *>, Script<*, *>>,
    val phases: List<SystemPhaseDraft>,
)
