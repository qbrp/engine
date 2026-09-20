package org.lain.engine.data

import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.WriteComponentAccess
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.setRequiredItemComponents
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.world.World

data class MaterializedComponents(
    val data: EntityPersistenceData?,
    val resolved: List<Component>,
    val unresolved: List<ComponentBatchDto>,
) {
    context(write: WriteComponentAccess)
    fun apply(entity: EntityId) {
        resolved.forEach { entity.setComponent(it) }
        data?.let { entity.configure(it) }
    }
}

fun EntityPersistentRecord.materialize(settings: ComponentLoadSettings, resolver: EntityResolver): MaterializedComponents {
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
    return MaterializedComponents(data, resolvedComponents, unresolvedComponents)
}

context(write: WriteComponentAccess)
fun EntityId.configure(data: EntityPersistenceData) {
    when (data) {
        is EntityPersistenceData.Item -> {
            setRequiredItemComponents(
                data.count, data.maxCount, data.prefabId.parse().toItemPrefabId()
            )
        }

        else -> {}
    }
}