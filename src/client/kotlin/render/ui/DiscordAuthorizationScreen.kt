package org.lain.engine.client.render.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.mc.literalText
import org.slf4j.LoggerFactory

class DiscordAuthorizationScreen(private val client: EngineClient) : Screen(TITLE) {
    private val authorizationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var authorizationMonitor: Job? = null

    @Volatile
    private var authorizationJob: Job? = null

    private lateinit var loginButton: Button
    private lateinit var autoLoginCheckbox: Checkbox
    private var authorizing = false
    private var status: Component? = null
    private var closed = false

    override fun init() {
        val panelWidth = (width - HORIZONTAL_MARGIN * 2).coerceAtMost(PANEL_WIDTH)
        val descriptionLines = font.split(DESCRIPTION, panelWidth - PANEL_PADDING * 2)
        val titleY = height / 2 - PANEL_HEIGHT / 2 + PANEL_PADDING
        val descriptionY = titleY + font.lineHeight + TITLE_DESCRIPTION_GAP
        val buttonY = descriptionY + descriptionLines.size * font.lineHeight + DESCRIPTION_BUTTON_GAP
        val checkboxY = buttonY + BUTTON_HEIGHT + BUTTON_CHECKBOX_GAP

        loginButton = addRenderableWidget(
            Button.builder(LOGIN_BUTTON) { startAuthorization() }
                .pos((width - BUTTON_WIDTH) / 2, buttonY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        )
        val autoLoginToggle = Checkbox.builder(AUTO_LOGIN, font)
            .selected(client.options.autoLogin)
            .maxWidth(panelWidth - PANEL_PADDING * 2)
            .onValueChange { _, selected -> client.options.autoLogin = selected }
            .build()
        autoLoginToggle.setPosition((width - autoLoginToggle.width) / 2, checkboxY)
        autoLoginCheckbox = addRenderableWidget(autoLoginToggle)
        updateControls()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)

        val panelTop = height / 2 - PANEL_HEIGHT / 2
        status?.let {
            guiGraphics.drawCenteredString(
                font,
                it,
                width / 2,
                panelTop + PANEL_HEIGHT - STATUS_BOTTOM_MARGIN,
                STATUS_COLOR,
            )
        }
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.renderBackground(guiGraphics, mouseX, mouseY, delta)

        val panelWidth = (width - HORIZONTAL_MARGIN * 2).coerceAtMost(PANEL_WIDTH)
        val panelTop = height / 2 - PANEL_HEIGHT / 2
        val titleY = panelTop + PANEL_PADDING
        val descriptionY = titleY + font.lineHeight + TITLE_DESCRIPTION_GAP

        guiGraphics.drawCenteredString(font, TITLE, width / 2, titleY, TITLE_COLOR)
        font.split(DESCRIPTION, panelWidth - PANEL_PADDING * 2)
            .forEachIndexed { index, line ->
                guiGraphics.drawCenteredString(
                    font,
                    line,
                    width / 2,
                    descriptionY + index * font.lineHeight,
                    DESCRIPTION_COLOR,
                )
            }
    }

    override fun onClose() {
        closed = true
        authorizationMonitor?.cancel()
        authorizationJob?.cancel()
        authorizationScope.cancel()
        super.onClose()
    }

    private fun startAuthorization() {
        if (authorizing) return

        authorizing = true
        status = OPENING_BROWSER
        updateControls()
        authorizationMonitor = authorizationScope.launch {
            try {
                val job = client.accountManager.authorizeDiscordOAuth2()
                authorizationJob = job
                job.join()
                MinecraftClient.execute {
                    if (closed) {
                        return@execute
                    }
                    if (client.accountManager.authorized) {
                        onClose()
                    } else {
                        authorizationFailed()
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                LOGGER.error("Не удалось начать авторизацию через Discord", exception)
                MinecraftClient.execute {
                    if (!closed) authorizationFailed()
                }
            }
        }
    }

    private fun authorizationFailed() {
        if (closed) return
        authorizing = false
        authorizationJob = null
        status = AUTHORIZATION_FAILED
        updateControls()
    }

    private fun updateControls() {
        if (!::loginButton.isInitialized || !::autoLoginCheckbox.isInitialized) return
        loginButton.active = !authorizing
        autoLoginCheckbox.active = !authorizing
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("Engine Discord Authorization")
        private val TITLE = literalText("Авторизация").withStyle(ChatFormatting.BOLD)
        private val DESCRIPTION = literalText(
            "Чтобы играть на серверах с Engine, требуется войти или создать аккаунт."
        )
        private val LOGIN_BUTTON = literalText("Войти через Discord")
        private val AUTO_LOGIN = literalText("Выполнять вход автоматически при запуске игры")
        private val OPENING_BROWSER = literalText("Открываем браузер для авторизации…")
        private val AUTHORIZATION_FAILED = literalText("Авторизация не завершена. Попробуйте ещё раз.")

        private const val PANEL_WIDTH = 460
        private const val PANEL_HEIGHT = 190
        private const val PANEL_PADDING = 24
        private const val HORIZONTAL_MARGIN = 16
        private const val BUTTON_WIDTH = 220
        private const val BUTTON_HEIGHT = 20
        private const val TITLE_DESCRIPTION_GAP = 12
        private const val DESCRIPTION_BUTTON_GAP = 24
        private const val BUTTON_CHECKBOX_GAP = 18
        private const val STATUS_BOTTOM_MARGIN = 22
        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val DESCRIPTION_COLOR = 0xFFBFD8FF.toInt()
        private const val STATUS_COLOR = 0xFFFFD37A.toInt()
    }
}
