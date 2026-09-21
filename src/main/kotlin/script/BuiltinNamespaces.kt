package org.lain.engine.script

import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemTooltip

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
        val INVALID_TOOLTIPS = listOf(
            "Помните, обилие багов - симптом активной разработки<newline>(C) lain1wakura",
            "i'm psyho",
            "если бы все мужчины были гомосексуальны, немецкий народ исчез бы,<newline>но если бы все женщины были лесбиянками, «они бы все равно рожали детей»",
            "Господи, храни америку!"
        )
        val INVALID = ItemPrefab(
            INVALID_ID,
            64,
            "Недействительный предмет",
            ItemAssets.withDefaultAsset(INVALID_ID.value),
            null,
            {
                it.setComponent(ItemTooltip(INVALID_TOOLTIPS.random()))
            }
        )
    }
}