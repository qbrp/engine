package org.lain.engine.mc

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player
import org.lain.cyberia.ecs.Component

val MinecraftServer.isReplayServer
    get() = this::class.qualifiedName == "com.moulberry.flashback.playback.ReplayServer"

val Player.isReplayViewer
    get() = this::class.qualifiedName == "com.moulberry.flashback.playback.ReplayPlayer"

object ReplayViewer : Component