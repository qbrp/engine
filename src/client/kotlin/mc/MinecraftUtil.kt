package org.lain.engine.client.mc

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.client.network.AbstractClientPlayerEntity
import org.lain.engine.client.EngineClient
import org.lain.engine.mc.EntityTable
import org.lain.engine.player.*
import org.lain.engine.transport.packet.ClientboundWorldData
import org.lain.engine.transport.packet.ServerPlayerData
import org.lain.engine.util.inject
import org.lain.engine.util.parseMiniMessage
import org.lain.engine.world.World

val MinecraftClient = net.minecraft.client.MinecraftClient.getInstance()!!

fun injectClient() = inject<EngineClient>()