package org.lain.engine.client.mc

import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.GameSession
import org.lain.engine.client.getClientItem
import org.lain.engine.mc.ENGINE_ITEM_REFERENCE_COMPONENT
import org.lain.engine.mc.EngineItemStack
import org.lain.engine.mc.MinecraftPlayer
import org.lain.engine.mc.visibleInventoryItems
import org.lain.engine.world.World
import kotlin.sequences.distinct
import kotlin.sequences.plus

fun World.tickClientInventorySyncSystem(gameSession: GameSession) {
    iterate<MinecraftPlayer> { _, minecraftPlayer ->
        val minecraftEntity = minecraftPlayer.entity
        minecraftPlayer.itemStacks =
            (minecraftEntity.visibleInventoryItems.asSequence() + sequenceOf(minecraftEntity.containerMenu.carried))
                .distinct()
                .mapNotNull { itemStack ->
                    val item = itemStack.get(ENGINE_ITEM_REFERENCE_COMPONENT)
                        ?.getClientItem(gameSession)
                        ?: return@mapNotNull null
                    EngineItemStack(item, itemStack)
                }
    }
}