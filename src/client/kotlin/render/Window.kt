package org.lain.engine.client.render

import org.lain.engine.client.MinecraftEngineClient
import org.lain.engine.client.mc.MinecraftChat
import org.lain.engine.client.mc.MinecraftClient
import org.lwjgl.glfw.GLFW

class Window(val mod: MinecraftEngineClient) {
    private val client = MinecraftClient
    private val window
        get() = client.window

    private var lastWindowWidth: Float = -1.0f
    private var lastWindowHeight: Float = -1.0f

    val widthDp: Float
        get() = client.window.scaledWidth.toFloat()
    val heightDp: Float
        get() = client.window.scaledHeight.toFloat()
    val scale: Float
        get() = client.window.scaleFactor.toFloat()

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
        val handle = window.handle
        return GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == 1
    }

    fun onResize() {
        MinecraftChat.channelsBar.measure()
        mod.uiRenderPipeline.resizeRoot()
    }
}
