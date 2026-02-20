package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.storage.addIfNotNull
import org.lain.engine.transport.packet.ItemComponent
import org.lain.engine.util.get

const val WRITEABLE_WRITTEN_ASSET = "writable_written"
const val WRITEABLE_EMPTY_ASSET = "writable_empty"

@Serializable
data class ItemAssets(val assets: Map<String, String>) : ItemComponent {
    fun copy() = ItemAssets(assets.toMap())
}

fun resolveItemAsset(item: EngineItem): String {
    val assets = item.get<ItemAssets>()?.assets ?: return "missingno"
    val variants = mutableListOf<String>()

    variants.addIfNotNull(
        item.get<Writable>()?.let { writeable ->
            if (writeable.contents.isEmpty()) {
                WRITEABLE_EMPTY_ASSET
            } else {
                WRITEABLE_WRITTEN_ASSET
            }
        }
    )

    return assets.filterKeys { variants.contains(it) }.values.firstOrNull() ?: assets["default"] ?: "missingno"
}