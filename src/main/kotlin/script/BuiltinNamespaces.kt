package org.lain.engine.script

import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab

object BuiltinNamespaces {
    val ERROR = Namespace(
        NamespaceId("core/error"),
        ContentHolder(
            mapOf(Items.INVALID_ID to Items.INVALID)
        )
    )

    val all = mapOf(
        ERROR.id to ERROR
    )

    object Items {
        val INVALID_ID = ItemId(EngineId("core/error/item"))
        val INVALID = ItemPrefab(
            INVALID_ID,
            64,
            "Недействительный предмет",
            ItemAssets.withDefaultAsset(INVALID_ID.value),
            null,
            {}
        )
    }
}