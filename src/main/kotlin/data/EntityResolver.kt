package org.lain.engine.data

import org.lain.cyberia.ecs.EntityId

interface EntityResolver {
    fun find(persistentId: PersistentId): EntityId?
    fun require(persistentId: PersistentId): EntityId {
        return find(persistentId) ?: error("Сущность с идентификатором $persistentId не найдена")
    }

    companion object {
        val EMPTY = object : EntityResolver {
            override fun find(persistentId: PersistentId): EntityId? = null
            override fun require(persistentId: PersistentId): EntityId {
                error("Entity resolver not supports relations")
            }
        }
    }
}