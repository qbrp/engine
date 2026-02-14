package org.lain.engine.server

import org.lain.engine.item.ItemUuid
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.Component
import org.lain.engine.util.require

data class SynchronizationComponent(
    var authorized: Boolean,
    val synchronizedPlayers: MutableList<EnginePlayer> = mutableListOf(),
    val synchronizedItems: MutableList<ItemUuid> = mutableListOf()
) : Component

val EnginePlayer.synchronization
    get() = this.require<SynchronizationComponent>()
