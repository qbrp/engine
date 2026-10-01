package org.lain.engine.player.interaction

import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.chat.ChatChannel
import org.lain.engine.chat.EngineChat
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.messageSource
import org.lain.engine.item.Item
import org.lain.engine.mc.displayNameMiniMessage
import org.lain.engine.player.DeveloperMode
import org.lain.engine.player.PlayerComponent
import org.lain.engine.player.PlayerInventory
import org.lain.engine.player.has
import org.lain.engine.player.remove
import org.lain.engine.player.username
import org.lain.engine.script.BuiltinNamespaces
import org.lain.engine.world.World

object TestCommand : Component {
    val VERB = Verb(
        VerbType("test", "Тестовое действие"),
        0,
        InputAction.Base
    ) { TestCommand }
}

data class TestHold(var ticks: Int = 20) : Component

fun World.collectTestAction() = iterate<VerbLookup, PlayerInventory, PlayerComponent>() { player, lookup, inventory, _ ->
    val developerMode = player.getComponent<DeveloperMode>()?.enabled == true
    val holdsInvalidItem = inventory.mainHandItem?.getComponent<Item>()?.id == BuiltinNamespaces.Items.INVALID_ID
    if (InputAction.Base in lookup.input && developerMode && holdsInvalidItem) {
        lookup.verbs += TestCommand.VERB
    }
}

fun World.tickTestAction(chat: EngineChat) {
    iterate<TestCommand, PlayerInventory> { player, _, inventory ->
        player.removeComponent<TestCommand>()
        player.setComponent(UsingItem(inventory.mainHandItem ?: return@iterate))
        player.setComponent(TestHold())
    }
    iterate<TestHold, PlayerComponent> { _, hold, (player) ->
        if (player.has<InteractionInterrupt>() || hold.ticks <= 0) {
            player.remove<TestHold>()
            return@iterate
        }
        chat.sendMessage(
            "Holding: ${hold.ticks}",
            MessageSource.getSystem(this@tickTestAction),
            ChatChannel.SYSTEM,
            recipient = player.messageSource(ChatChannel.SYSTEM)
        )
        hold.ticks--
    }
}