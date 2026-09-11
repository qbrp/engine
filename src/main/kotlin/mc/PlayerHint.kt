package org.lain.engine.mc

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.requireComponent
import org.lain.engine.player.DisplayName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.character.CharacterDisplay
import org.lain.engine.player.displayName
import org.lain.engine.player.get
import org.lain.engine.player.require
import org.lain.engine.player.username
import org.lain.engine.world.World

context(world: World)
fun showPlayerHint(player: ServerPlayer, of: EnginePlayer) {
    val characterName = of.get<CharacterDisplay>()?.name?.gradientChars
    val name = of.get<DisplayName>() ?: return
    val username = name.username.value
    val customName = characterName ?: name.custom?.gradientText

    val message = Component.empty()

    message.append(customName?.getText() ?: Component.literal(username))
    if (customName != null) {
        message.append(" <gray>($username)</gray>".parseMiniMessage())
    }

    player.displayClientMessage(
        message,
        true
    )
}