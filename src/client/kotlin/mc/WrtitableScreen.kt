package org.lain.engine.client.mc

import net.minecraft.client.gui.screens.inventory.BookEditScreen
import net.minecraft.server.network.Filterable
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.component.WritableBookContent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.client.GameSession
import org.lain.engine.item.WritableOpen
import org.lain.engine.player.PlayerComponent
import org.lain.engine.world.World
import java.util.Optional

fun GameSession.tickWritableUiSystem() {
    val mainPlayerEntity = MinecraftClient.player
    world.iterate<WritableOpen, PlayerComponent>() { e, (writable), (player) ->
        if (player == mainPlayer) {
            val player = mainPlayerEntity ?: return@iterate
            val itemStack = player.mainHandItem
            itemStack.set(
                DataComponents.WRITABLE_BOOK_CONTENT,
                WritableBookContent(writable.contents.map { Filterable(it, Optional.empty()) })
            )
            MinecraftClient.setScreen(BookEditScreen(player, itemStack, InteractionHand.MAIN_HAND))
        }
        e.removeComponent<WritableOpen>()
    }
}
