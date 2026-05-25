package org.lain.engine.client

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.mc.EngineItemReferenceComponent
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.inject

class ClientItemStorage : Storage<PersistentId, EngineItem>(), ItemAccess {
    override fun getItem(uuid: PersistentId): EngineItem? {
        return this.get(uuid)
    }
}

fun injectClientItemStorage() = inject<ClientItemStorage>()

fun EngineItemReferenceComponent.getClientItem(): EngineItem? {
    val itemStorage by injectClientItemStorage()
    return itemStorage.get(uuid)
}