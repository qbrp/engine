package org.lain.engine.client.render.ui.chat

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.util.CommonColors
import org.lain.engine.client.EngineClient
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mixin.chat.ChatScreenAccessor
import org.lain.engine.client.render.HAND
import org.lain.engine.client.render.ui.ColorMc
import org.lain.engine.client.render.ui.drawEngineSprite
import org.lain.engine.mc.Text
import org.lain.engine.player.extendArm
import org.lain.engine.util.Color

class HandStatusButtonWidget(val client: EngineClient, x: Int, y: Int, width: Int, height: Int, message: Text) : AbstractWidget(x, y, width, height, message) {
    override fun renderWidget(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float
    ) {
        var alpha = 0.5f
        if (client.gameSession?.mainPlayer?.extendArm == true) {
            alpha += 0.4f
        }
        if (isMouseOver(mouseX.toDouble(), mouseY.toDouble())) {
            alpha += 0.1f
        }
        context.fill(x, y, width + x, height + y, MinecraftClient.options.getBackgroundColor(Int.MIN_VALUE))
        context.drawEngineSprite(
            HAND,
            x.toFloat(),
            y.toFloat(),
            width.toFloat(),
            height.toFloat(),
            Color(ColorMc.color(alpha, CommonColors.WHITE))
        )
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        client.gameSession?.apply { extendArm = !extendArm }
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        return (MinecraftClient.screen as? ChatScreenAccessor)?.`engine$getChatField`()?.charTyped(codePoint, modifiers) ?: false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return (MinecraftClient.screen as? ChatScreenAccessor)?.`engine$getChatField`()?.keyPressed(keyCode, scanCode, modifiers) ?: false
    }

    override fun keyReleased(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        return (MinecraftClient.screen as? ChatScreenAccessor)?.`engine$getChatField`()?.keyReleased(keyCode, scanCode, modifiers) ?: false
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}
}