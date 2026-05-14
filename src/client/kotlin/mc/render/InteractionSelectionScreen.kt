package org.lain.engine.client.mc.render

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.ChatFormatting
import net.minecraft.client.GameNarrator
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.CommonColors
import org.lain.cyberia.ecs.get
import org.lain.cyberia.ecs.handle
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.KeybindManager
import org.lain.engine.mc.Text
import org.lain.engine.mc.engineId
import org.lain.engine.mc.literalText
import org.lain.engine.mc.vanillaId
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.InteractionSelection

class InteractionSelectionScreen(
    val gameSession: GameSession,
    val selection: InteractionSelection,
    val keybindManager: KeybindManager,
) : Screen(GameNarrator.NO_TITLE) {
    private var lastMouseX = 0
    private var lastMouseY = 0
    private var mouseUsedForSelection = false
    private val variants = selection.variants
    private val variantButtons = mutableListOf<ButtonWidget>()
    private var variant = selection.variants.first()
    private val uiWidth = variants.size * 31 - 5
    private var ticks = 0

    override fun init() {
        super.init()
        this.variantButtons.clear()
        this.variant = selection.variants.first()
        selection.variants.forEachIndexed { index, variant ->
            variantButtons.add(
                ButtonWidget(
                    variant,
                    this.width / 2 - uiWidth / 2 + index * 31,
                    this.height / 2 - 31
                )
            )
        }
    }

    override fun tick() {
        super.tick()
        ticks++
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.drawCenteredString(
            this.font,
            this.variant.name,
            this.width / 2,
            this.height / 2 - 31 - 20,
            CommonColors.WHITE
        )
        context.drawCenteredString(
            this.font,
            SELECT_NEXT_TEXT,
            this.width / 2,
            this.height / 2 + 5,
            CommonColors.WHITE
        )
        if (!this.mouseUsedForSelection) {
            this.lastMouseX = mouseX
            this.lastMouseY = mouseY
            this.mouseUsedForSelection = true
        }
        val bl = this.lastMouseX == mouseX && this.lastMouseY == mouseY
        for (buttonWidget in variantButtons) {
            buttonWidget.renderWidget(context, mouseX, mouseY, deltaTicks)
            buttonWidget.setSelected(this.variant == buttonWidget.variant)
            if (bl || !buttonWidget.isActive) continue
            this.variant = buttonWidget.variant
        }
    }

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val i = this.width / 2 - 62
        val j = this.height / 2 - 31 - 27
        context.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            i,
            j,
            0.0f,
            0.0f,
            125,
            75,
            128,
            128
        )
    }

    private fun apply() {
        val interactionComponent = gameSession.mainPlayer.get<InteractionComponent>()
        if (interactionComponent != null && interactionComponent.selection == selection) {
            interactionComponent.selectionVariant = this.variant
            interactionComponent.selection = null
            gameSession.handler.onInteractionSelectionSelect(variant.id)
        }
    }

    private fun discard() {
        gameSession.mainPlayer.handle<InteractionComponent>() {
            selectionVariant = null
            selection = null
            selectionCancelled = true
            gameSession.handler.onInteractionSelectionSelect(null)
        }
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == InputConstants.KEY_F4) {
            this.mouseUsedForSelection = false
            val index = variants.indexOf(variant) + 1
            variant = variants[index % variants.size]
            return true
        } else if (input.key() == InputConstants.KEY_RETURN) {
            this.apply()
            this.minecraft!!.setScreen(null)
        } else if (input.key() == InputConstants.KEY_ESCAPE) {
            discard()
            this.minecraft!!.setScreen(null)
        }
        return super.keyPressed(input)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    @Environment(value = EnvType.CLIENT)
    class ButtonWidget(
        val variant: InteractionSelection.Variant,
        x: Int,
        y: Int
    ) : AbstractWidget(x, y, 26, 26, literalText(variant.name)) {
        private val textureId = engineId(variant.asset)
        private var selected = false

        public override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            this.drawBackground(context)
            if (this.selected) {
                this.drawSelectionBox(context)
            }
            if (variant.isItem) {
                context.drawFakeEngineItem(textureId, this.variant.name, this.x + 5, this.y + 5)
            } else {
                context.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    x + 5,
                    y + 5,
                    width,
                    height
                )
            }
        }

        override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}

        override fun isActive(): Boolean {
            return super.isActive() || this.selected
        }

        fun setSelected(selected: Boolean) {
            this.selected = selected
        }

        private fun drawBackground(context: GuiGraphics) {
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                x,
                y,
                26,
                26
            )
        }

        private fun drawSelectionBox(context: GuiGraphics) {
            context.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                SELECTION_TEXTURE,
                x,
                y,
                26,
                26
            )
        }
    }

    companion object {
        val SLOT_TEXTURE: Identifier = vanillaId("gamemode_switcher/slot")
        val SELECTION_TEXTURE: Identifier = vanillaId("gamemode_switcher/selection")
        private val TEXTURE: Identifier = vanillaId("textures/gui/container/gamemode_switcher.png")
        private val SELECT_NEXT_TEXT: Text = Text.translatable(
            "debug.gamemodes.select_next",
                Text.translatable(
                    "debug.gamemodes.press_f4"
                ).withStyle(ChatFormatting.AQUA)
            )
    }
}