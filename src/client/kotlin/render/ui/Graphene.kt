package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.TextAlignment
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.lain.engine.client.EngineClient
import org.lain.engine.client.EngineMinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
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

fun builtinWebPageUrl(path: String) = UI.appAssets().asset("web/$path.html")

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
                            .fileRoot("engine/web")
                            .build()
                    )
                    .build()
            )
            .global(
                GrapheneGlobalConfig.builder()
                    .jcefDownloadPath(Path.of("./graphene-jcef"))
                    .extensionFolder(Path.of("./engine/extensions"))
                    .remoteDebugging(
                        GrapheneRemoteDebugConfig.builder()
                            .randomPort()
                            .allowedOrigins("https://chrome-devtools-frontend.appspot.com")
                            .build()
                    )
                    .allowFileSystemAccess()
                    .build()
            )
            .build()
    )
}

fun Screen.widgetWithMargins(margin: Int, url: String, marginYDown: Int = 0): GrapheneWebViewWidget {
    return GrapheneWebViewWidget(
        this,
        margin,
        margin ,
        width - margin * 2,
        height - margin * 2 - marginYDown,
        Component.empty(),
        url
    )
}

class TestGrapheneScreen(private val engineClient: EngineClient) : Screen(literalText("Graphene test")) {
    override fun init() {
        val margin = 4
        val top = 128

        addRenderableWidget(
            GrapheneWebViewWidget(
                this,
                margin,
                top,
                width - margin * 2,
                height - top - margin,
                Component.empty(),
                webPageUrl("web_test")
            )
        )
    }

    override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(guiGraphics, i, j, f)

        val textRenderer = guiGraphics.textRenderer()
        val titleScale = 2.0f
        val titleY = 6
        val titleParameters = textRenderer.defaultParameters().withScale(titleScale)
        textRenderer.accept(
            TextAlignment.CENTER,
            (width / titleScale / 2).toInt(),
            (titleY / titleScale).toInt(),
            titleParameters,
            TEXT_TITLE
        )

        val subtextMaxWidth = width - 64
        val subtextY = titleY + (minecraft.font.lineHeight * titleScale).toInt() + 10
        val subtextLineHeight = minecraft.font.lineHeight + 2
        minecraft.font.split(SUBTEXT_TITLE, subtextMaxWidth).forEachIndexed { index, line ->
            textRenderer.accept(
                TextAlignment.CENTER,
                width / 2,
                subtextY + index * subtextLineHeight,
                line
            )
        }
    }

    override fun onClose() {
        minecraft.setScreen(DiscordAuthorizationScreen(engineClient))
    }

    companion object {
        private val TEXT_TITLE = literalText("Работает ли Graphene?").withStyle(Style.EMPTY.withBold(true))
        private val SUBTEXT_TITLE = ("Для отрисовки графического интерфейса Engine использует движок Chromium." +
                "<newline>Если под этой надписью ничего не видно, значит библиотека корректно не инициализировалась. Свяжитесь с разработчиком.").parseMiniMessageClient()
    }
}

class HintEditScreen : Screen(literalText("Hint editor")) {
    private lateinit var view: GrapheneWebViewWidget

    protected override fun init() {
        view = widgetWithMargins(8, builtinWebPageUrl("hint_editor"))
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
            builtinWebPageUrl(lastPage ?: "debug")
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

    protected override fun init() {
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
