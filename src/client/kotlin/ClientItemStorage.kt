package org.lain.engine.client

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemAccess
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.EngineItemReferenceComponent
import org.lain.engine.util.Storage
import org.lain.engine.util.inject

class ClientItemStorage : Storage<ItemUuid, EngineItem>(), ItemAccess {
    override fun getItem(uuid: ItemUuid): EngineItem? {
        return this.get(uuid)
    }
}

fun injectClientItemStorage() = inject<ClientItemStorage>()

fun EngineItemReferenceComponent.getClientItem(): EngineItem? {
    return cachedItem ?: run {
        val itemStorage by injectClientItemStorage()
        val item = itemStorage.get(uuid)
        cachedItem = item
        item
    }
}