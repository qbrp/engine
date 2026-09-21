package org.lain.engine.data

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.setRequiredItemComponents
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.util.ecs.EntityId

data class MaterializedComponents(
    val persistentId: PersistentId,
    val data: EntityPersistenceData?,
    val resolved: List<Component>,
    val unresolved: List<ComponentBatchDto>,
) {
    @Suppress("UNCHECKED_CAST")
    context(write: WriteComponentAccess)
    fun apply(entity: EntityId) {
        resolved.forEach { component ->
            write.setComponentWithType(
                entity,
                component,
                componentTypeOf(component) as ComponentType<Component>,
            )
        }
        data?.let { entity.configure(persistentId, it) }
    }
}

fun EntityPersistentRecord.materialize(settings: ComponentReviveSettings, resolver: EntityResolver): MaterializedComponents {
    val resolvedComponents = mutableListOf<Component>()
    val unresolvedComponents = mutableListOf<ComponentBatchDto>()
    components.forEach { componentRecord ->
        try {
            val revived = componentRecord.decode().revive(resolver, settings)
            resolvedComponents += revived
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
    return MaterializedComponents(uuid, data, resolvedComponents, unresolvedComponents)
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
