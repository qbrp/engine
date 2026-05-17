package org.lain.engine.client.render

import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.mc.MinecraftChat
import org.lain.engine.client.mc.MinecraftClient
import org.lwjgl.glfw.GLFW

class Window(val mod: EngineMinecraftClient) {
    private val client = MinecraftClient
    private val window
        get() = client.window

    private var lastWindowWidth: Float = -1.0f
    private var lastWindowHeight: Float = -1.0f

    val widthDp: Float
        get() = client.window.guiScaledWidth.toFloat()
    val heightDp: Float
        get() = client.window.guiScaledHeight.toFloat()
    val scale: Float
        get() = client.window.guiScale.toFloat()

    fun handleResize() {
        val currentW = widthDp
        val currentH = heightDp

        if (currentW != lastWindowWidth || currentH != lastWindowHeight) {
            onResize()
        }

        lastWindowWidth = currentW
        lastWindowHeight = currentH
    }

    fun isMinimized(): Boolean {
        val handle = window.handle()
        return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == 1
    }

    fun onResize() {
        MinecraftChat.channelsBar.measure()
        mod.uiRenderPipeline.resizeRoot()
    }
}
