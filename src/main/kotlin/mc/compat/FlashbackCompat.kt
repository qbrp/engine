package org.lain.engine.mc.compat

import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.player.Player

val MinecraftServer.isReplayServer
    get() = this::class.qualifiedName == "com.moulberry.flashback.playback.ReplayServer"

val Player.isReplayViewer
    get() = this::class.qualifiedName == "com.moulberry.flashback.playback.ReplayPlayer" || name.string.startsWith("Replay Viewer")