package org.lain.engine.client.mc.render

import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.client.util.InputUtil
import net.minecraft.client.util.NarratorManager
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.KeybindManager
import org.lain.engine.player.InteractionComponent
import org.lain.engine.player.InteractionSelection
import org.lain.engine.util.EngineId
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle

class InteractionSelectionScreen(
    val gameSession: GameSession,
    val selection: InteractionSelection,
    val keybindManager: KeybindManager,
) : Screen(NarratorManager.EMPTY) {
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

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            this.variant.name,
            this.width / 2,
            this.height / 2 - 31 - 20,
            Colors.WHITE
        )
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            SELECT_NEXT_TEXT,
            this.width / 2,
            this.height / 2 + 5,
            Colors.WHITE
        )
        if (!this.mouseUsedForSelection) {
            this.lastMouseX = mouseX
            this.lastMouseY = mouseY
            this.mouseUsedForSelection = true
        }
        val bl = this.lastMouseX == mouseX && this.lastMouseY == mouseY
        for (buttonWidget in variantButtons) {
            buttonWidget.render(context, mouseX, mouseY, deltaTicks)
            buttonWidget.setSelected(this.variant == buttonWidget.variant)
            if (bl || !buttonWidget.isSelected) continue
            this.variant = buttonWidget.variant
        }
    }

    override fun renderBackground(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val i = this.width / 2 - 62
        val j = this.height / 2 - 31 - 27
        context.drawTexture(
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

    override fun keyPressed(input: KeyInput): Boolean {
        if (input.key() == InputUtil.GLFW_KEY_F4) {
            this.mouseUsedForSelection = false
            val index = variants.indexOf(variant) + 1
            variant = variants[index % variants.size]
            return true
        } else if (input.key() == InputUtil.GLFW_KEY_ENTER) {
            this.apply()
            this.client!!.setScreen(null)
        } else if (input.key() == InputUtil.GLFW_KEY_ESCAPE) {
            discard()
            this.client!!.setScreen(null)
        }
        return super.keyPressed(input)
    }

    override fun shouldPause(): Boolean {
        return false
    }

    @Environment(value = EnvType.CLIENT)
    class ButtonWidget(
        val variant: InteractionSelection.Variant,
        x: Int,
        y: Int
    ) : ClickableWidget(x, y, 26, 26, Text.of(variant.name)) {
        private val textureId = EngineId(variant.asset)
        private var selected = false

        public override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, deltaTicks: Float) {
            this.drawBackground(context)
            if (this.selected) {
                this.drawSelectionBox(context)
            }
            if (variant.isItem) {
                context.drawFakeEngineItem(textureId, this.variant.name, this.x + 5, this.y + 5)
            } else {
                context.drawGuiTexture(
                    RenderPipelines.GUI_TEXTURED,
                    textureId,
                    x + 5,
                    y + 5,
                    width,
                    height
                )
            }
        }

        public override fun appendClickableNarrations(builder: NarrationMessageBuilder?) {
            this.appendDefaultNarrations(builder)
        }

        override fun isSelected(): Boolean {
            return super.isSelected() || this.selected
        }

        fun setSelected(selected: Boolean) {
            this.selected = selected
        }

        private fun drawBackground(context: DrawContext) {
            context.drawGuiTexture(
                RenderPipelines.GUI_TEXTURED,
                SLOT_TEXTURE,
                x,
                y,
                26,
                26
            )
        }

        private fun drawSelectionBox(context: DrawContext) {
            context.drawGuiTexture(
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
        val SLOT_TEXTURE: Identifier = Identifier.ofVanilla("gamemode_switcher/slot")
        val SELECTION_TEXTURE: Identifier = Identifier.ofVanilla("gamemode_switcher/selection")
        private val TEXTURE: Identifier = Identifier.ofVanilla("textures/gui/container/gamemode_switcher.png")
        private val SELECT_NEXT_TEXT: Text = Text.translatable(
            "debug.gamemodes.select_next", *arrayOf<Any?>(
                Text.translatable(
                    "debug.gamemodes.press_f4"
                ).formatted(Formatting.AQUA)
            )
        )
    }
}