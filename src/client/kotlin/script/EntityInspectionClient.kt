package org.lain.engine.client.script

import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.EntityInspectorWindow
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.script.dev.EntityInspectionSnapshot
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getEntityDebugNameId

class EntityInspection(
    private val gameSession: GameSession,
    val entity: EntityId,
    val persistentId: PersistentId
) {
    private var aborted: Boolean = false
    var data: EntityInspectionSnapshot.Dto? = null
        private set
    var rate: Int = 20
        private set
    lateinit var window: EntityInspectorWindow

    fun abort(reason: String) {
        window.abort(reason)
        aborted = true
        gameSession.entityInspections.remove(persistentId)
    }

    fun updateData(data: EntityInspectionSnapshot.Dto) {
        if (aborted) return
        this.data = data
        window.onDataUpdated(data)
    }

    fun updateRate(rate: Int) {
        if (aborted) return
        this.rate = rate
        gameSession.handler.onEntityInspection(persistentId, rate)
    }

    fun editValue(objectId: Int, key: String, value: InspectionPrimitive) {
        if (aborted) return
        gameSession.handler.onEntityInspectionEdit(persistentId, objectId, key, value)
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

fun GameSession.startInspection(entityId: EntityId): EntityInspection = with(world) {
    val persistentId = entityId.persistentId()
    return EntityInspection(this@startInspection, entityId, persistentId).also {
        it.window = EntityInspectorWindow(it, entityId.getEntityDebugNameId().name, namespacedStorage)
        entityInspections[persistentId] = it
        handler.onEntityInspection(persistentId, it.rate)
        client.imGuiManager.addWindow(it.window)
    }
}
