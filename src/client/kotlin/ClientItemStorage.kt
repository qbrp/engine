package org.lain.engine.client

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.mc.EngineItemReferenceComponent
import org.lain.engine.storage.PersistentId
import org.lain.engine.util.Storage
import org.lain.engine.util.inject

class ClientItemStorage : Storage<String, EngineItem>(), ItemAccess {
    override fun getItem(uuid: PersistentId): EngineItem? {
        return this.get(uuid.value)
    }
}

fun injectClientItemStorage() = inject<ClientItemStorage>()

fun EngineItemReferenceComponent.getClientItem(): EngineItem? {
    val itemStorage by injectClientItemStorage()
    return itemStorage.get(uuid.value)
}