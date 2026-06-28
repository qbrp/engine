package org.lain.engine.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lain.cyberia.ecs.*
import org.lain.engine.item.Item
import org.lain.engine.item.UpdateMeta
import org.lain.engine.util.DebugName
import org.lain.engine.util.EntityDebugId
import org.lain.engine.util.EntityDebugNameId
import org.lain.engine.util.component.Networked
import org.lain.engine.util.getDebugId
import org.lain.engine.util.getEntityDebugNameId
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

interface EntityProvider {
    suspend fun loadEntity(persistentId: PersistentId): List<ComponentDto>?
}

class EntityResolver(private val entityProvider: EntityProvider) {
    private val hydrated = ConcurrentHashMap.newKeySet<PersistentId>()
    private val entities = ConcurrentHashMap<PersistentId, EntityId>()
    private val _resolved = Collections.synchronizedSet(HashSet<EntityId>())
    val resolved: Set<EntityId>
        get() = _resolved

    context(write: WriteComponentAccess)
    suspend fun loadEntity(
        componentLoadSettings: ComponentLoadSettings,
        components: List<ComponentDto>,
        persistentId: PersistentId? = null,
    ): EntityId {
        val entity = persistentId?.let { componentLoadSettings.persistentIdToEntity[it] } ?: write.addEntity()
        try {
            val domainComponents = components.toDomainSuspend {
                toDomainSuspend(
                    componentLoadSettings,
                    entityGetter = { persistentId ->
                        resolveReferencedEntity(
                            componentLoadSettings,
                            persistentId
                        )
                    }
                ).also { component ->
                    if (component is Item && persistentId != null) {
                        entity.setComponent(UpdateMeta(false))
                        entity.setComponent(Networked)
                    }
                }
            }

            entity.copyState(domainComponents)
            entity
        } catch (e: Exception) {
            entity.destroy()
            throw e
        }
        return entity
    }

    context(write: WriteComponentAccess)
    private suspend fun resolveReferencedEntity(
        componentLoadSettings: ComponentLoadSettings,
        persistentId: PersistentId
    ): EntityId? {
        // если сущность уже загружена в мир - возвращаем её
        componentLoadSettings.persistentIdToEntity[persistentId]?.let {
            _resolved += it
            return it
        }

        // в противном случае создаем плейсхолдер
        val entity = resolvePlaceholder(persistentId)
        if (!hydrated.add(persistentId)) {
            return entity
        }

        try {
            val components = withContext(Dispatchers.IO) {
                entityProvider.loadEntity(persistentId)
            } ?: return null

            val domainComponents = components.toDomainSuspend {
                val component = toDomainSuspend(
                    componentLoadSettings,
                    entityGetter = { refPersistentId ->
                        resolveReferencedEntity(
                            componentLoadSettings,
                            refPersistentId
                        )
                    }
                )
                component
            }

            _resolved += entity
            entity.copyState(domainComponents)
            return entity
        } catch (e: Exception) {
            hydrated.remove(persistentId)
            entities.remove(persistentId)

            entity.destroy()

            throw e
        }
    }

    context(write: WriteComponentAccess)
    private fun resolvePlaceholder(persistentId: PersistentId): EntityId {
        entities[persistentId]?.let { return it }

        val placeholder = write.addEntity()
        val prev = entities.putIfAbsent(persistentId, placeholder)

        return if (prev != null) {
            placeholder.destroy()
            prev
        } else {
            placeholder
        }
    }
}