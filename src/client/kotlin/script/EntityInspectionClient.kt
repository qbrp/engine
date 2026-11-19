package org.lain.engine.client.script

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.EntityInspectorWindow
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.script.EntityInspectionSnapshotDto
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.util.property.PropertyEdit
import org.lain.engine.util.property.PropertyPathDto

class EntityInspection(
    private val gameSession: GameSession,
    val entity: EntityId,
    val persistentId: PersistentId
) {
    private var aborted: Boolean = false
    var data: EntityInspectionSnapshotDto? = null
        private set
    var rate: Int = 5
        private set
    lateinit var window: EntityInspectorWindow

    fun abort(reason: String) {
        window.abort(reason)
        aborted = true
        gameSession.entityInspections.remove(persistentId)
    }

    fun updateData(data: EntityInspectionSnapshotDto) {
        if (aborted) return
        this.data = data
        window.onDataUpdated(data)
    }

    fun updateRate(rate: Int) {
        if (aborted) return
        this.rate = rate
        gameSession.handler.onEntityInspection(persistentId, rate)
    }

    fun editValue(componentType: String, path: PropertyPathDto, editOperation: PropertyEdit) {
        if (aborted) return
        val snapshot = data ?: return
        gameSession.handler.onEntityInspectionEdit(persistentId, snapshot.snapshotId, componentType, path, editOperation)
    }

    fun markDirty(componentType: String) {
        if (aborted) return
        gameSession.handler.onEntityInspectionMarkDirty(persistentId, componentType)
    }

    fun stop() {
        gameSession.handler.onEntityInspectionStop(persistentId)
        gameSession.entityInspections.remove(persistentId)
    }
}

fun GameSession.startInspection(entityId: EntityId): Unit = with(world) {
    val persistentId = entityId.persistentId()
    EntityInspection(this@startInspection, entityId, persistentId).also {
        it.window = EntityInspectorWindow(it, entityId.getEntityDebugNameId().name, namespacedStorage)
        entityInspections[persistentId] = it
        handler.onEntityInspection(persistentId, it.rate)
        client.imGuiManager.addWindow(it.window)
    }
}
