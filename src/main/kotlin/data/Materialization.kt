package org.lain.engine.data

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.setRequiredItemComponents
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.script.ExecutionResult
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.ScriptContext
import org.lain.engine.script.ScriptValue
import org.lain.engine.util.ecs.EntityId

data class MaterializedComponents(
    val persistentId: PersistentId,
    val data: EntityPersistenceData?,
    val resolved: List<Component>,
    val unresolved: List<ComponentBatchDto>,
    val migrations: List<PlannedMigration>
) {
    @Suppress("UNCHECKED_CAST")
    context(write: WriteComponentAccess)
    fun applyResolved(entity: EntityId) {
        resolved.forEach { component ->
            write.setComponentWithType(
                entity,
                component,
                componentTypeOf(component) as ComponentType<Component>,
            )
        }
    }

    context(write: WriteComponentAccess)
    fun apply(entity: EntityId, entityLoad: PendingEntityLoad) {
        applyResolved(entity)
        entityLoad.planMigration(migrations)
        data?.let { entity.configure(persistentId, it) }
    }
}

fun EntityPersistentRecord.materialize(
    settings: ComponentReviveSettings,
    resolver: EntityResolver
): MaterializedComponents {
    val resolvedComponents = mutableListOf<Component>()
    val unresolvedComponents = mutableListOf<ComponentBatchDto>()
    val migrations = mutableListOf<PlannedMigration>()
    components.forEach { componentRecord ->
        try {
            val snapshot = componentRecord.decode()
            resolvedComponents += if (snapshot is ComponentSnapshot.Script) {
                val component = snapshot.revive(resolver, settings)
                component.planMigration(componentRecord.version)
                    ?.let { migrations += it }
                component
            } else {
                snapshot.revive(resolver, settings)
            }
        } catch (e: Exception) {
            unresolvedComponents.add(
                ComponentBatchDto(
                    uuid,
                    componentRecord.copy(
                        error = e.message ?: "Unresolved error: ${e.toString()}"
                    )
                )
            )
            LOGGER.error(
                "Не удалось загрузить компонент {} ({}) сущности {}: ",
                componentRecord.id,
                componentRecord.version,
                uuid,
                e
            )
            return@forEach
        }
    }
    return MaterializedComponents(uuid, data, resolvedComponents, unresolvedComponents, migrations)
}

context(write: WriteComponentAccess)
fun EntityId.configure(persistentId: PersistentId, data: EntityPersistenceData) {
    setComponent(PersistentIdComponent(persistentId))
    when (data) {
        is EntityPersistenceData.Item -> {
            setRequiredItemComponents(
                data.count, data.maxCount, data.prefabId.parse().toItemPrefabId()
            )
        }

        else -> {}
    }
}
