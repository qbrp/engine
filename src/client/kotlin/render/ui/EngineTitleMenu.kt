package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.Util
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.PlainTextButton
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen
import net.minecraft.client.gui.screens.options.LanguageSelectScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import org.lain.engine.client.EngineClient
import org.lain.engine.client.account.ConnectionState
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.client.render.MAP
import org.lain.engine.mc.engineId
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin

class EngineTitleMenu(
    private var fading: Boolean = true,
    private val client: EngineClient
) : Screen(TITLE) {
    private val random = RandomSource.create()
    private var animationTicks = 0.0f
    private var fadeInStart = 0L
    private var singleplayerButton: Button? = null
    private var multiplayerButton: ColoredPlainTextButton? = null
    private var authorizationButton: ColoredPlainTextButton? = null

    override fun init() {
        val menuX = MENU_PADDING.coerceAtMost(
            (width - BUTTON_WIDTH - MENU_PADDING).coerceAtLeast(MENU_PADDING)
        )
        val logoY = (height / 4 - 50).coerceAtLeast(18)
        var y = (logoY + LOGO_HEIGHT + 40).coerceAtMost(height - 118)

        y = createNormalMenuOptions(menuX, y)
        y = createTestWorldButton(menuX, y)
        y += BUTTON_STEP + 16

        val plainTextButton = PlainTextButton(
            menuX,
            y,
            86,
            15,
            Component.translatable("menu.options"),
            { MinecraftClient.setScreen(OptionsScreen(this, MinecraftClient.options)) },
            font
        )
        addRenderableWidget(plainTextButton)

        y += BUTTON_STEP

        val accessibilityButton = PlainTextButton(
            menuX,
            y,
            86,
            15,
            Component.translatable("options.accessibility"),
            { MinecraftClient.setScreen(AccessibilityOptionsScreen(this, MinecraftClient.options)) },
            font
        )
        addRenderableWidget(accessibilityButton)

        y += BUTTON_STEP

        val languageButton = PlainTextButton(
            menuX,
            y,
            86,
            15,
            Component.translatable("options.language"),
            {
                MinecraftClient.setScreen(
                    LanguageSelectScreen(
                        this,
                        MinecraftClient.options,
                        MinecraftClient.languageManager
                    )
                )
            },
            font
        )
        addRenderableWidget(languageButton)

        val copyrightWidth = font.width(COPYRIGHT_TEXT)
        addRenderableWidget(
            PlainTextButton(
                width - copyrightWidth - 2,
                height - 10,
                copyrightWidth,
                10,
                COPYRIGHT_TEXT,
                { MinecraftClient.setScreen(CreditsAndAttributionScreen(this)) },
                font
            )
        )

        addRenderableWidget(
            PlainTextButton(
                menuX,
                height - BUTTON_STEP - 2,
                86,
                20,
                Component.translatable("menu.quit"),
                { MinecraftClient.stop() },
                font
            )
        )

        val authorizationStatus = authorizationStatus(client)
        authorizationButton = addRenderableWidget(
            ColoredPlainTextButton(
                authorizationStatusX(width, font, authorizationStatus.text),
                AUTHORIZATION_STATUS_PADDING,
                font.width(authorizationStatus.text),
                AUTHORIZATION_BUTTON_HEIGHT,
                Component.literal(authorizationStatus.text),
                { ClientMixin.setDiscordAuthorizationScreen() },
                font,
                authorizationStatus.color
            )
        )

        if (fading) {
            if (fadeInStart == 0L) {
                fadeInStart = Util.getMillis()
            }
            setWidgetsAlpha(0.0f)
        }
    }

    override fun tick() {
        singleplayerButton?.active = client.canPlaySingleplayer
        multiplayerButton?.let { button ->
            button.active = client.canPlayMultiplayer
            button.textColor = multiplayerButtonColor(client.canPlayMultiplayer)
        }
        authorizationButton?.let { button ->
            val status = authorizationStatus(client)
            val buttonWidth = font.width(status.text)
            button.setMessage(Component.literal(status.text))
            button.setWidth(buttonWidth)
            button.setX(authorizationStatusX(width, font, status.text))
            button.textColor = status.color
            button.message = Component.literal(status.text)
            button.active = client.connectionState !is ConnectionState.Authorizing
        }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        animationTicks += delta

        if (fadeInStart == 0L && fading) {
            fadeInStart = Util.getMillis()
        }

        var alpha = 1.0f
        if (fading) {
            val progress = (Util.getMillis() - fadeInStart).toFloat() / FADE_IN_DURATION_MS
            if (progress > 1.0f) {
                fading = false
            } else {
                alpha = ((progress.coerceIn(0.0f, 1.0f) - 0.5f) / 0.5f).coerceIn(0.0f, 1.0f)
            }
            setWidgetsAlpha(alpha)
        }

        super.render(guiGraphics, mouseX, mouseY, delta)

        val logoY = (height / 4 - 40).coerceAtLeast(38)
        renderLogo(guiGraphics, MENU_PADDING, logoY, alpha)
    }

    private fun setWidgetsAlpha(alpha: Float) {
        children().filterIsInstance<AbstractWidget>().forEach { it.setAlpha(alpha) }
    }

    override fun renderBackground(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        MovingWallpapers.render(guiGraphics, delta)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return if (Screen.hasControlDown() && keyCode == InputConstants.KEY_F10) {
            MinecraftClient.setScreen(TitleScreen())
            true
        } else if (Screen.hasControlDown() && keyCode == InputConstants.KEY_R) {
            MovingWallpapers.loadWallpapers(MinecraftClient)
            client.showNotification(
                LittleNotification(
                    "Обои перезагружены",
                    sprite = MAP,
                ),
            )
            true
        } else {
            super.keyPressed(keyCode, scanCode, modifiers)
        }
    }

    override fun isPauseScreen(): Boolean = false

    override fun shouldCloseOnEsc(): Boolean = false

    private fun createNormalMenuOptions(x: Int, y: Int): Int {
        val multiplayerText = Component.translatable("menu.multiplayer")
        multiplayerButton = addRenderableWidget(
            ColoredPlainTextButton(
                x, y, BUTTON_WIDTH, 15, multiplayerText, {
                    val screen = if (minecraft!!.options.skipMultiplayerWarning) {
                        JoinMultiplayerScreen(this)
                    } else {
                        SafetyScreen(this)
                    }
                    minecraft!!.setScreen(screen)
                },
                font,
                multiplayerButtonColor(client.canPlayMultiplayer)
            )
        ).also {
            it.active = client.canPlayMultiplayer
        }

        singleplayerButton = addRenderableWidget(
            PlainTextButton(
                x,
                y + BUTTON_STEP,
                BUTTON_WIDTH,
                20,
                Component.translatable("menu.singleplayer"),
                { MinecraftClient.setScreen(SelectWorldScreen(this)) },
                font
            )
        ).also {
            it.active = client.canPlaySingleplayer
        }

        return y + BUTTON_STEP
    }

    private fun createTestWorldButton(x: Int, y: Int): Int {
        if (!SharedConstants.IS_RUNNING_IN_IDE) return y
        val nextY = y + BUTTON_STEP
        addRenderableWidget(
            Button.builder(Component.literal("Create Test World")) {
                CreateWorldScreen.openFresh(MinecraftClient, this)
            }.bounds(x, nextY, BUTTON_WIDTH, 20).build()
        )
        return nextY
    }

    private fun renderLogo(guiGraphics: GuiGraphics, x: Int, y: Int, alpha: Float) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toDouble(), y.toDouble(), 0.0)
        renderAnimatedLogoLine(guiGraphics, "qbrp", 0.0f, 0.0f, LOGO_TOP_SCALE, alpha, 0, RED_HUE)
        guiGraphics.pose().popPose()
    }

    private fun renderAnimatedLogoLine(
        guiGraphics: GuiGraphics,
        text: String,
        x: Float,
        y: Float,
        scale: Float,
        alpha: Float,
        letterOffset: Int,
        baseHue: Float
    ) {
        guiGraphics.pose().pushPose()
        guiGraphics.pose().translate(x.toDouble(), y.toDouble(), 0.0)
        guiGraphics.pose().scale(scale, scale, 1f)

        var letterX = 0
        text.forEachIndexed { index, letter ->
            val glyph = logoText(letter.toString())
            guiGraphics.drawString(
                font,
                glyph,
                letterX,
                0,
                animatedLogoColor(index + letterOffset, alpha, baseHue),
                false
            )
            letterX += font.width(glyph)
        }

        guiGraphics.pose().popPose()
    }

    private fun logoText(text: String): Component {
        return Component.literal(text).withStyle { it.withFont(ALVERA_FONT) }
    }

    private fun animatedLogoColor(index: Int, alpha: Float, baseHue: Float): Int {
        val wave =
            sin((animationTicks * LOGO_COLOR_SPEED + index * LOGO_LETTER_PHASE) * PI * 2.0).toFloat()
        val hue = normalizeHue(baseHue + wave * LOGO_HUE_RANGE)

        val rgb = Mth.hsvToRgb(hue, 0.85f, 0.85f)
        val a = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()

        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun normalizeHue(hue: Float): Float {
        return ((hue % 1.0f) + 1.0f) % 1.0f
    }

    private class ColoredPlainTextButton(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        message: Component,
        onPress: OnPress,
        private val font: Font,
        var textColor: Int
    ) : PlainTextButton(x, y, width, height, message, onPress, font) {
        override fun renderWidget(
            guiGraphics: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            delta: Float
        ) {
            val message = if (isHoveredOrFocused && isActive) {
                getMessage().copy().withStyle(Style.EMPTY.withUnderlined(true))
            } else {
                getMessage()
            }
            val color = (ceil(alpha * 255.0f).toInt() shl 24) or (textColor and 0x00FFFFFF)
            guiGraphics.drawString(font, message, x, y, color)
        }
    }

    companion object {
        private val TITLE: Component = Component.translatable("narrator.screen.title")
        private val COPYRIGHT_TEXT: Component = Component.translatable("title.credits")
        private val ALVERA_FONT = engineId("alvera")
        private const val LOGO_TOP_SCALE = 0.85f
        private const val LOGO_ENGINE_SCALE = 0.85f
        private const val LOGO_ENGINE_X_OFFSET = 4.0f
        private const val LOGO_ENGINE_Y = 42.0f
        private const val LOGO_HEIGHT = 50
        private const val LOGO_COLOR_SPEED = 0.015f
        private const val LOGO_LETTER_PHASE = 0.09f
        private const val MENU_PADDING = 8
        private const val BUTTON_WIDTH = 200
        private const val BUTTON_STEP = 18
        private const val AUTHORIZATION_STATUS_PADDING = 4
        private const val AUTHORIZATION_BUTTON_HEIGHT = 10
        private const val FADE_IN_DURATION_MS = 2_000.0f
        private const val RED_HUE = 0.0f
        private const val GREEN_HUE = 1.0f / 3.0f
        private const val LOGO_HUE_RANGE = 0.085f

        private data class AuthorizationStatus(val text: String, val color: Int)

        private fun authorizationStatus(client: EngineClient): AuthorizationStatus {
            return when (client.connectionState) {
                is ConnectionState.Authorizing -> AuthorizationStatus(
                    "Авторизация",
                    0xFFFFFF55.toInt()
                )

                is ConnectionState.Authorized -> AuthorizationStatus(
                    "Авторизован",
                    0xFF55FF55.toInt()
                )

                else -> AuthorizationStatus(
                    "Не авторизован. Игра на серверах Engine недоступна",
                    0xFFFF5555.toInt()
                )
            }
        }

        private fun authorizationStatusX(screenWidth: Int, font: Font, text: String): Int {
            return screenWidth - font.width(text) - AUTHORIZATION_STATUS_PADDING
        }

        private fun multiplayerButtonColor(canPlayMultiplayer: Boolean): Int {
            return if (canPlayMultiplayer) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
        }
    }
}
