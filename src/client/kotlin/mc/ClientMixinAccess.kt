package org.lain.engine.client.mc

import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.util.Injector

object ClientMixinAccess {
    private val client by injectClient()

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible.get()

    fun onKey(key: Int) {
        client.onKey(key)
    }

    fun sendChatMessage(content: String) {
        val gameSession = client.gameSession ?: return
        gameSession.chatManager.sendMessage(content)
    }

    fun getResourceList(): ResourceList {
        return Injector.resolve(ResourceList::class)
    }

    fun createResourceList(): ResourceList {
        return findAssets().also { Injector.register(it) }
    }
}