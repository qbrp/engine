package org.lain.engine.client.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lain.engine.client.handler.TickDispatcher
import org.lain.engine.client.mc.MinecraftClient
import kotlin.coroutines.CoroutineContext

object MinecraftClientDispatcher : CoroutineDispatcher() {
    private val tickDispatcher = TickDispatcher()

    fun confirmTick() = tickDispatcher.tick()

    suspend fun waitNextTick() {
        tickDispatcher.waitNextTick()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        MinecraftClient.execute(block)
    }
}

suspend fun <T> withClientContext(block: suspend CoroutineScope.(MinecraftClientDispatcher) -> T) = withContext(MinecraftClientDispatcher) {
    block(MinecraftClientDispatcher)
}

fun <T> launchClientContext(block: suspend CoroutineScope.(MinecraftClientDispatcher) -> T) {
    CoroutineScope(MinecraftClientDispatcher).launch {
        block(MinecraftClientDispatcher)
    }
}