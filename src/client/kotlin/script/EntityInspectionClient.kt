package org.lain.engine.client.script

import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.EntityInspectorWindow
import org.lain.engine.client.render.ui.ImGuiWindow
import org.lain.engine.data.PersistentId
import org.lain.engine.data.persistentId
import org.lain.engine.script.EntityDebugData
import org.lain.engine.util.ecs.EntityId

data class EntityInspection(
    val entity: EntityId,
    val persistentId: PersistentId,
    var data: EntityDebugData.Dto? = null
) {
    lateinit var window: ImGuiWindow
}

fun GameSession.startInspection(entityId: EntityId): EntityInspection = with(world) {
    val persistentId = entityId.persistentId()
    return EntityInspection(entityId, persistentId, null).also {
        it.window = EntityInspectorWindow(it)
        entityInspection = it
        handler.onEntityDebugView(persistentId)
        client.imGuiManager.addWindow(it.window)
    }
}