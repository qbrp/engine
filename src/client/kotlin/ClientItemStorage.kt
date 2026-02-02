package org.lain.engine.client

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemUuid
import org.lain.engine.mc.EngineItemReferenceComponent
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerId
import org.lain.engine.util.Storage
import org.lain.engine.util.inject
import org.lain.engine.util.injectItemStorage

class ClientItemStorage : Storage<ItemUuid, EngineItem>()

fun injectClientItemStorage() = inject<ClientItemStorage>()

fun EngineItemReferenceComponent.getClientItem(): EngineItem? {
    if (uuid == null) return null
    return cachedItem ?: run {
        val itemStorage by injectClientItemStorage()
        val item = itemStorage.get(uuid!!)
        cachedItem = item
        item
    }
}