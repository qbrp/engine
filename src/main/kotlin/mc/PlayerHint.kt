package org.lain.engine.mc

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.DisplayName
import org.lain.engine.world.World

context(world: World)
fun showPlayerHint(player: ServerPlayer, of: EntityId) {
    val name = of.requireComponent<DisplayName>()
    val username = name.username.value
    val customName = name.custom

    val message = Component.empty()

    message.append(customName?.gradientText?.getText() ?: Component.literal(username))
    if (customName != null) {
        message.append(" <gray>($username)</gray>".parseMiniMessage())
    }

    player.displayClientMessage(
        message,
        true
    )
}