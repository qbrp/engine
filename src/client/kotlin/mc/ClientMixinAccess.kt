package org.lain.engine.client.mc

import org.lain.engine.client.resources.ResourceList
import org.lain.engine.client.resources.findAssets
import org.lain.engine.util.Injector
import org.lain.engine.util.injectValue

object ClientMixinAccess {
    private val client by injectClient()
    private var resources: ResourceList? = null

    fun isEngineLoaded(): Boolean {
        val isPlayerInstantiated = client.gameSession?.mainPlayer != null
        return isPlayerInstantiated
    }

    fun onScroll(vertical: Float) {
        client.onScroll(vertical)
    }

    fun isScrollAllowed() = client.gameSession?.movementManager?.locked ?: true

    fun isCrosshairAttackIndicatorVisible() = client.options.crosshairIndicatorVisible

    fun onKey(key: Int) {
        client.onKey(key)
    }

    fun sendChatMessage(content: String) {
        val gameSession = client.gameSession ?: return
        gameSession.chatManager.sendMessage(content)
    }

    fun getResourceList(): ResourceList {
        return resources ?: findAssets().also { resources = it }
    }

    fun createResourceList(): ResourceList {
        resources = findAssets()
        return resources!!
    }

    fun getKeybindManager(): KeybindManager = injectValue()
}