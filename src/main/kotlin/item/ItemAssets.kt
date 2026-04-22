package org.lain.engine.item

import org.lain.cyberia.ecs.getComponent
import org.lain.engine.util.addIfNotNull
import org.lain.engine.world.World

const val WRITEABLE_WRITTEN_ASSET = "writable_written"
const val WRITEABLE_EMPTY_ASSET = "writable_empty"

context(world: World)
fun resolveItemAsset(item: EngineItem): String = with(world) {
    val assets = item.getComponent<ItemAssets>()?.assets ?: return "missingno"
    val variants = mutableListOf<String>()

    variants.addIfNotNull(
        item.getComponent<Writable>()?.let { writeable ->
            if (writeable.contents.isEmpty()) {
                WRITEABLE_EMPTY_ASSET
            } else {
                WRITEABLE_WRITTEN_ASSET
            }
        }
    )

    return assets.filterKeys { variants.contains(it) }.values.firstOrNull() ?: assets["default"] ?: "missingno"
}