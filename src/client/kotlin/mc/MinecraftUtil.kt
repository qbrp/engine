package org.lain.engine.client.mc

import org.lain.engine.client.EngineClient
import org.lain.engine.util.inject

val MinecraftClient = net.minecraft.client.MinecraftClient.getInstance()!!

fun injectClient() = inject<EngineClient>()