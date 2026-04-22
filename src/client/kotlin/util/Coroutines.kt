package org.lain.engine.client.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.lain.engine.client.mc.MinecraftClient
import kotlin.coroutines.CoroutineContext

object MinecraftClientDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        MinecraftClient.execute(block)
    }
}