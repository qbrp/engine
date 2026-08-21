package org.lain.engine.client.render.ui

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.SharedConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
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
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import org.lain.engine.client.EngineClient
import org.lain.engine.client.account.ConnectionState
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.mc.engineId
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class EngineTitleMenu(
    private var fading: Boolean = true,
    private val client: EngineClient
) : Screen(TITLE) {
    private val random = RandomSource.create()
    private var animationTicks = 0.0f
    private var singleplayerButton: Button? = null
    private var multiplayerButton: Button? = null

    override fun init() {
        val menuX = MENU_PADDING.coerceAtMost((width - BUTTON_WIDTH - MENU_PADDING).coerceAtLeast(MENU_PADDING))
        val logoY = (height / 4 - 50).coerceAtLeast(18)
        var y = (logoY + LOGO_HEIGHT + 40).coerceAtMost(height - 118)

        y = createNormalMenuOptions(menuX, y)
        y = createTestWorldButton(menuX, y)
        y += BUTTON_STEP + 16

        val plainTextButton = PlainTextButton(
            menuX,
            y,
            86,
            20,
            Component.translatable("menu.options"),
            { minecraft.setScreen(OptionsScreen(this, minecraft.options)) },
            font
        )
        addRenderableWidget(plainTextButton)

        y += BUTTON_STEP

        val accessibilityButton = PlainTextButton(
            menuX,
            y,
            86,
            20,
            Component.translatable("options.accessibility"),
            { minecraft.setScreen(AccessibilityOptionsScreen(this, minecraft.options)) },
            font
        )
        addRenderableWidget(accessibilityButton)

        y += BUTTON_STEP

        val languageButton = PlainTextButton(
            menuX,
            y,
            86,
            20,
            Component.translatable("options.language"),
            { minecraft.setScreen(LanguageSelectScreen(this, minecraft.options, minecraft.languageManager)) },
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
                { minecraft.setScreen(CreditsAndAttributionScreen(this)) },
                font
            )
        )

        addRenderableWidget(
            PlainTextButton(
                menuX, height - BUTTON_STEP - 2, 86, 20, Component.translatable("menu.quit"), { minecraft.stop() }, font
            )
        )
    }

    override fun tick() {
        singleplayerButton?.active = client.canPlaySingleplayer
        multiplayerButton?.active = client.canPlayMultiplayer
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        animationTicks += delta

        var alpha = 1.0f
        if (fading) {
            val progress = animationTicks / FADE_IN_TICKS
            if (progress > 1.0f) {
                fading = false
            } else {
                alpha = Mth.clampedMap(Mth.clamp(progress, 0.0f, 1.0f), 0.5f, 1.0f, 0.0f, 1.0f)
            }
            fadeWidgets(alpha)
        }

        super.render(guiGraphics, mouseX, mouseY, delta)

        val logoY = (height / 4 - 40).coerceAtLeast(38)
        renderLogo(guiGraphics, MENU_PADDING, logoY, alpha)
        renderAuthorizationStatus(guiGraphics, mouseX, mouseY)
    }

    override fun renderBackground(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        MovingWallpapers.render(guiGraphics, delta)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return if (keyEvent.key == InputConstants.KEY_F10) {
            minecraft.setScreen(TitleScreen())
            true
        } else {
            super.keyPressed(keyEvent)
        }
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_1) {
            val status = authorizationStatus(client)
            if (isAuthorizationStatusHovered(click.x(), click.y(), status.text)) {
                ClientMixin.setDiscordAuthorizationScreen()
                return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun isPauseScreen(): Boolean = false

    override fun shouldCloseOnEsc(): Boolean = false

    override fun canInterruptWithAnotherScreen(): Boolean = true

    private fun createNormalMenuOptions(x: Int, y: Int): Int {
        multiplayerButton = addRenderableWidget(
            PlainTextButton(
                x, y + BUTTON_STEP, BUTTON_WIDTH, 20, Component.translatable("menu.multiplayer"), {
                    val screen = if (minecraft!!.options.skipMultiplayerWarning) {
                        JoinMultiplayerScreen(this)
                    } else {
                        SafetyScreen(this)
                    }
                    minecraft!!.setScreen(screen)
                },
                font
            )
        ).also {
            it.active = client.canPlayMultiplayer
        }

        singleplayerButton = addRenderableWidget(
            PlainTextButton(
                x,
                y,
                BUTTON_WIDTH,
                20,
                Component.translatable("menu.singleplayer"),
                { minecraft.setScreen(SelectWorldScreen(this)) },
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
                CreateWorldScreen.testWorld(minecraft, { minecraft!!.setScreen(this) })
            }.bounds(x, nextY, BUTTON_WIDTH, 20).build()
        )
        return nextY
    }

    private fun renderLogo(guiGraphics: GuiGraphics, x: Int, y: Int, alpha: Float) {
        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().translate(x.toFloat(), y.toFloat())
        renderAnimatedLogoLine(guiGraphics, "qbrp", 0.0f, 0.0f, LOGO_TOP_SCALE, alpha, 0, RED_HUE)
        val engineX = font.width(logoText("qb")) * LOGO_TOP_SCALE + LOGO_ENGINE_X_OFFSET
        renderAnimatedLogoLine(guiGraphics, "engine", engineX, LOGO_ENGINE_Y, LOGO_ENGINE_SCALE, alpha, 4, GREEN_HUE)
        guiGraphics.pose().popMatrix()
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
        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().translate(x, y)
        guiGraphics.pose().scale(scale, scale)

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

        guiGraphics.pose().popMatrix()
    }

    private fun logoText(text: String): Component {
        return Component.literal(text).withStyle { it.withFont(ALVERA_FONT) }
    }

    private fun animatedLogoColor(index: Int, alpha: Float, baseHue: Float): Int {
        val wave = sin((animationTicks * LOGO_COLOR_SPEED + index * LOGO_LETTER_PHASE) * PI * 2.0).toFloat()
        val hue = normalizeHue(baseHue + wave * LOGO_HUE_RANGE)

        val rgb = Mth.hsvToRgb(hue, 0.85f, 0.85f)
        val a = (alpha.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()

        return (a shl 24) or (rgb and 0x00FFFFFF)
    }

    private fun normalizeHue(hue: Float): Float {
        return ((hue % 1.0f) + 1.0f) % 1.0f
    }

    private fun renderAuthorizationStatus(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val status = authorizationStatus(client)
        val x = authorizationStatusX(guiGraphics.guiWidth(), font, status.text)
        val y = AUTHORIZATION_STATUS_PADDING

        guiGraphics.drawString(font, status.text, x, y, status.color)

        if (isAuthorizationStatusHovered(mouseX.toDouble(), mouseY.toDouble(), status.text)) {
            guiGraphics.fill(
                x,
                y + font.lineHeight,
                x + font.width(status.text),
                y + font.lineHeight + 1,
                status.color
            )
        }
    }

    private fun isAuthorizationStatusHovered(mouseX: Double, mouseY: Double, text: String): Boolean {
        val x = authorizationStatusX(width, font, text)
        val y = AUTHORIZATION_STATUS_PADDING
        return mouseX >= x && mouseX <= x + font.width(text) && mouseY >= y && mouseY <= y + font.lineHeight
    }

    companion object {
        private val TITLE: Component = Component.translatable("narrator.screen.title")
        private val COPYRIGHT_TEXT: Component = Component.translatable("title.credits")
        private val ALVERA_FONT = FontDescription.Resource(engineId("alvera"))
        private const val LOGO_TOP_SCALE = 0.85f
        private const val LOGO_ENGINE_SCALE = 0.85f
        private const val LOGO_ENGINE_X_OFFSET = 4.0f
        private const val LOGO_ENGINE_Y = 42.0f
        private const val LOGO_HEIGHT = 70
        private const val LOGO_COLOR_SPEED = 0.015f
        private const val LOGO_LETTER_PHASE = 0.09f
        private const val MENU_PADDING = 8
        private const val BUTTON_WIDTH = 200
        private const val BUTTON_STEP = 18
        private const val AUTHORIZATION_STATUS_PADDING = 4
        private const val FADE_IN_TICKS = 40.0f
        private const val RED_HUE = 0.0f
        private const val GREEN_HUE = 1.0f / 3.0f
        private const val LOGO_HUE_RANGE = 0.085f

        private data class AuthorizationStatus(val text: String, val color: Int)

        private fun authorizationStatus(client: EngineClient): AuthorizationStatus {
            return when (client.connectionState) {
                is ConnectionState.Authorizing -> AuthorizationStatus("Авторизация", 0xFFFFFF55.toInt())
                is ConnectionState.Authorized -> AuthorizationStatus("Авторизован", 0xFF55FF55.toInt())
                else -> AuthorizationStatus(
                    "Не авторизован. Игра на серверах Engine недоступна",
                    0xFFFF5555.toInt()
                )
            }
        }

        private fun authorizationStatusX(screenWidth: Int, font: Font, text: String): Int {
            return screenWidth - font.width(text) - AUTHORIZATION_STATUS_PADDING
        }
    }
}
