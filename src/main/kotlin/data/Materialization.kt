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
    fun apply(entity: EntityId) {
        applyResolved(entity)
        data?.let { entity.configure(persistentId, it) }
    }
}

class MigrationException(val version: Int, val targetVersion: Int, cause: Throwable? = null) :
    RuntimeException("Не удалось выполнить миграцию с версии $version до $targetVersion", cause)

fun ComponentSnapshot.Script.migrated(baseVersion: Int, type: ScriptComponentType): ComponentSnapshot.Script? {
    var version = baseVersion
    val targetVersion = type.version

    if (baseVersion == targetVersion) return null

    var value: ScriptValue = value
    while (version < targetVersion) {
        val result = type.migrations[version].execute(
            ScriptContext.ComponentMigration(value)
        )
        value = when (result) {
            is ExecutionResult.Failure<ScriptValue> -> throw MigrationException(version, targetVersion, result.error)
            is ExecutionResult.Success<ScriptValue> -> result.value
        }
        version++
    }
    return copy(value = value)
}

fun ComponentSnapshot.Script.reviveScriptComponentMigrating(
    record: ComponentPersistentRecord,
    resolver: EntityResolver,
    settings: ComponentReviveSettings
): ScriptComponent {
    val type = settings.resolveScriptComponentType(scriptId)
    val snapshotToRevive = migrated(record.version, type) ?: this
    return snapshotToRevive.revive(resolver, settings, type)
}

fun ComponentPersistentRecord.decodeRevive(
    resolver: EntityResolver,
    settings: ComponentReviveSettings
): Component {
    val snapshot = decode()
    return if (snapshot is ComponentSnapshot.Script) {
        snapshot.reviveScriptComponentMigrating(this, resolver, settings)
    } else {
        snapshot.revive(resolver, settings)
    }
}

fun EntityPersistentRecord.materialize(
    settings: ComponentReviveSettings,
    resolver: EntityResolver
): MaterializedComponents {
    val resolvedComponents = mutableListOf<Component>()
    val unresolvedComponents = mutableListOf<ComponentBatchDto>()
    components.forEach { componentRecord ->
        try {
            resolvedComponents += componentRecord.decodeRevive(resolver, settings)
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
