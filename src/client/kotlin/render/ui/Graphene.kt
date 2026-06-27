package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.resources.ResourceContext
import org.lain.engine.mc.literalText
import org.lain.engine.util.file.ENGINE_DIR
import tytoo.grapheneui.api.GrapheneCore
import tytoo.grapheneui.api.config.GrapheneConfig
import tytoo.grapheneui.api.config.GrapheneContainerConfig
import tytoo.grapheneui.api.config.GrapheneGlobalConfig
import tytoo.grapheneui.api.config.GrapheneHttpConfig
import tytoo.grapheneui.api.config.GrapheneRemoteDebugConfig
import tytoo.grapheneui.api.widget.GrapheneWebViewWidget
import java.nio.file.Path

val UI by lazy { GrapheneCore.handle(EngineMinecraftClient::class.java) }

fun webPageUrl(path: String) = UI.httpUrl("$path.html")

fun initializeGraphene() {
    GrapheneCore.register(
        EngineMinecraftClient::class.java,
        GrapheneConfig.builder()
            .container(
                GrapheneContainerConfig.builder()
                .http(
                    GrapheneHttpConfig.builder()
                    .bindHost("127.0.0.1")
                    .randomPortInRange(20_000, 21_000)
                    .spaFallback("/not_found.html")
                    .fileRoot(ENGINE_DIR.resolve("web").toPath().toAbsolutePath())
                    .build())
                .build())
            .global(GrapheneGlobalConfig.builder()
                .jcefDownloadPath(Path.of("./graphene-jcef"))
                .extensionFolder(Path.of("./engine/extensions"))
                .remoteDebugging(
                    GrapheneRemoteDebugConfig.builder()
                    .randomPort()
                    .allowedOrigins("https://chrome-devtools-frontend.appspot.com")
                    .build())
                .allowFileSystemAccess()
                .build())
            .build()
    )
}

class HintEditScreen() : Screen(literalText("Hint editor")) {
    private lateinit var view: GrapheneWebViewWidget

    protected override fun init() {
        val margin = 8
        view = GrapheneWebViewWidget(
            this,
            margin,
            margin,
            width - margin * 2,
            height - margin * 2,
            Component.empty(),
            webPageUrl("hint_editor")
        )

        addRenderableWidget(view)
    }
}

class WebDebugScreen(private val resourceContext: ResourceContext) : Screen(literalText("Web Debug")) {
    private lateinit var view: GrapheneWebViewWidget

    protected override fun init() {
        val lastPage = lastPage
        val margin = 8
        val editBoxHeight = minecraft.font.lineHeight + 2
        view = GrapheneWebViewWidget(
            this,
            margin,
            margin,
            width - margin * 2,
            height - margin * 3 - editBoxHeight,
            Component.empty(),
            webPageUrl(lastPage ?: "debug")
        )
        addRenderableWidget(view)

        val buttonWidth = 9
        val editBox = EditBox(
            minecraft.font,
            margin,
            height - margin * 2,
            width - margin * 2 - buttonWidth,
            editBoxHeight,
            Component.empty(),
        )
        if (lastPage != null) {
            editBox.value = lastPage
        }
        val button = Button.builder(literalText("+")) {
            val url = editBox.value
            view.loadUrl(webPageUrl(url))
            Companion.lastPage = url
        }
            .pos(width - margin, height - margin)
            .size(buttonWidth, editBoxHeight)
            .build()
        addRenderableWidget(editBox)
        addRenderableWidget(button)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        if (keyEvent.key() == InputConstants.KEY_F5) {
            view.reload()
            return true
        }
        return super.keyPressed(keyEvent)
    }

    companion object {
        var lastPage: String? = null
    }
}

data class WebWidgetSizeParameters(val x: Int, val y: Int, val width: Int, val height: Int)

data class WebWidgetScreenParameters(val pause: Boolean, val background: Boolean)

open class WebScreen(
    val resourceContext: ResourceContext,
    val url: String,
    val parameters: WebWidgetScreenParameters,
    val sizeResolver: (Int, Int) -> WebWidgetSizeParameters
) : Screen(literalText("Web Screen")) {
    lateinit var widget: GrapheneWebViewWidget
    var onClose: (() -> Unit)? = null
    val unsubscribes: MutableList<() -> Unit> = mutableListOf()

    protected override fun init() {
        unsubscribes.forEach { it() }
        val (x, y, width, height) = sizeResolver(width, height)
        widget = GrapheneWebViewWidget(
            this,
            x,
            y,
            width,
            height,
            Component.empty(),
            webPageUrl(url)
        )
        addRenderableWidget(widget)
    }

    override fun onClose() {
        super.onClose()
        widget.close()
        onClose?.invoke()
        unsubscribes.forEach { it() }
    }

    override fun isPauseScreen(): Boolean {
        return parameters.pause
    }

    override fun renderBackground(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        if (parameters.background) {
            super.renderBackground(guiGraphics, i, j, f)
        }
    }
}