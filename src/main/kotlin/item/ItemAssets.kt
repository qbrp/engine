package org.lain.engine.item

import kotlinx.serialization.Serializable
import org.lain.engine.storage.addIfNotNull
import org.lain.engine.util.Component
import org.lain.engine.util.get

const val WRITEABLE_WRITTEN_ASSET = "writable_written"
const val WRITEABLE_EMPTY_ASSET = "writable_written"

@Serializable
data class ItemAssets(val assets: Map<String, String>) : Component

fun resolveItemAsset(item: EngineItem): String? {
    val assets = item.get<ItemAssets>()?.assets ?: return null
    val variants = mutableListOf<String>()

    variants.addIfNotNull(
        item.get<Writable>()?.let { writeable ->
            return if (writeable.contents.isEmpty()) {
                WRITEABLE_EMPTY_ASSET
            } else {
                WRITEABLE_WRITTEN_ASSET
            }
        }
    )

    return assets
        .filterKeys { variants.contains(it) }.keys.firstOrNull()
}