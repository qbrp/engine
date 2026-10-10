package org.lain.engine.client.script

import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.EntityInspectorWindow
import org.lain.engine.client.render.ui.ImGuiWindow
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.script.dev.EntityInspectionSnapshot
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.getEntityDebugNameId

class EntityInspection(
    private val gameSession: GameSession,
    val entity: EntityId,
    val persistentId: PersistentId,
    var data: EntityInspectionSnapshot.Dto? = null,
) {
    var rate: Int = 20
        private set
    lateinit var window: ImGuiWindow

    fun updateRate(rate: Int) {
        this.rate = rate
        gameSession.handler.onEntityDebugView(persistentId, rate)
    }

    fun stop() {
        gameSession.handler.onEntityDebugViewStop(persistentId)
        gameSession.entityInspections.remove(persistentId)
    }
}

fun GameSession.startInspection(entityId: EntityId): EntityInspection = with(world) {
    val persistentId = entityId.persistentId()
    return EntityInspection(this@startInspection, entityId, persistentId, null).also {
        it.window = EntityInspectorWindow(it, entityId.getEntityDebugNameId().name)
        entityInspections[persistentId] = it
        handler.onEntityDebugView(persistentId, it.rate)
        client.imGuiManager.addWindow(it.window)
    }
}
