package org.lain.engine.client

import org.lain.engine.item.EngineItem
import org.lain.engine.mc.ecs.EngineItemReferenceComponent
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage

class ClientItemStorage : Storage<PersistentId, EngineItem>()

fun EngineItemReferenceComponent.getClientItem(gameSession: GameSession): EngineItem? {
    return gameSession.itemStorage.get(uuid)
}