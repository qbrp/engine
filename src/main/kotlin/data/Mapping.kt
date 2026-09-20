package org.lain.engine.data

import kotlinx.coroutines.runBlocking
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.copyState
import org.lain.engine.item.EngineItem
import org.lain.engine.player.interaction.ActionSyncEvent
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.EntityRpcQueue
import org.lain.engine.script.EntityRpcReceiver
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.SInt
import org.lain.engine.script.SList
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptEngine
import org.lain.engine.util.Storage
import java.util.LinkedList

data class ComponentLoadSettings(
    val itemStorage: Storage<PersistentId, EngineItem>,
    val namespacedStorage: NamespacedStorageAccess,
    val persistentIdToEntity: MutableMap<PersistentId, EntityId>,
    val scriptEngine: ScriptEngine,
    val isClient: Boolean = false
)

suspend fun List<ComponentDto>.toDomainSuspend(toDomainFunction: suspend ComponentDto.() -> Component?): List<Component> {
    return mapNotNull { componentDto -> componentDto.toDomainCatching(toDomainFunction) }
}

context(write: WriteComponentAccess)
suspend fun EntityId.copyComponentDtoState(
    components: List<ComponentDto>,
    transformer: suspend List<ComponentDto>.(suspend ComponentDto.() -> Component?) -> List<Component> = List<ComponentDto>::toDomainSuspend,
    toDomainFunction: ComponentDto.() -> Component?,
) {
    copyState(components.transformer(toDomainFunction))
}
