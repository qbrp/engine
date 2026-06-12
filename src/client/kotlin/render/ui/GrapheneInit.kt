package org.lain.engine.client.render.ui

import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.literalText
import tytoo.grapheneui.api.GrapheneCore
import tytoo.grapheneui.api.config.GrapheneConfig
import tytoo.grapheneui.api.config.GrapheneGlobalConfig
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget


fun initializeGraphene() {
    GrapheneCore.register(
        "engine",
        GrapheneConfig.builder()
            .global(
                GrapheneGlobalConfig.builder()
                    .allowFileSystemAccess()
                    .build()
            )
            .build()
    )
}

fun openTestGrapheneWindow() {
    MinecraftClient.setScreen(TestWebScreen())
}

class TestWebScreen : Screen(literalText("Test Web Screen")) {
    protected override fun init() {
        val margin = 8
        val webX = margin
        val webY = margin
        val webWidth: Int = width - margin * 2
        val webHeight: Int = height - margin * 2

        addRenderableWidget(
            GrapheneWebViewWidget(
                this,
                webX,
                webY,
                webWidth,
                webHeight,
                Component.empty(),
                "https://www.google.com"
            )
        )
    }
}