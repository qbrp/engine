package org.lain.engine.item

import org.lain.cyberia.ecs.getComponent
import org.lain.engine.util.addIfNotNull
import org.lain.engine.world.World

const val WRITEABLE_WRITTEN_ASSET = "writable_written"
const val WRITEABLE_EMPTY_ASSET = "writable_empty"

context(world: World)
fun resolveItemAsset(item: EngineItem): String {
    val assets = item.getComponent<ItemAssets>()?.assets ?: return "missingno"

    val writable = item.getComponent<Writable>()
    if (writable != null) {
        val variant = if (writable.contents.isEmpty()) {
            WRITEABLE_EMPTY_ASSET
        } else {
            WRITEABLE_WRITTEN_ASSET
        }

        assets[variant]?.let { return it.full }
    }

    return assets["default"]?.full ?: "missingno"
}