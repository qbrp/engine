package org.lain.engine.client.render

import org.lain.engine.item.EngineItem
import org.lain.engine.item.ShootTag
import org.lain.engine.item.recoilSpeed
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.shake
import org.lain.engine.util.get
import org.lain.engine.world.events
import org.lain.engine.world.world

fun handleItemShakes(player: EnginePlayer, items: Collection<EngineItem>) {
    items.forEach { item ->
        val shootTag = item.get<ShootTag>() ?: return@forEach
        player.shake(shootTag.recoilSpeed / 2f)
        player.world.events.shoots += shootTag.shoot
        item.removeComponent(shootTag)
    }
}