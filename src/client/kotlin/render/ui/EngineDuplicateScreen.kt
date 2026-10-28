package org.lain.engine.client.render.ui

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.ModOrigin
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.FormattedCharSequence
import org.lain.engine.mc.Text
import java.io.File

class EngineDuplicateScreen(
    duplicates: List<File>
) : Screen(Text.literal("Дубликат версии")) {
    private val message = Text.literal("У вас установлено несколько экземпляров Engine:\n").append(
        Text.literal(
            duplicates
                .map { it.nameWithoutExtension }
                .sorted()
                .joinToString("\n")
        ).withStyle(ChatFormatting.YELLOW)
    )
    private val instruction = Text.literal("Удалите дубликаты и перезапустите игру")
    private var messageLines: List<FormattedCharSequence> = emptyList()
    private var instructionLines: List<FormattedCharSequence> = emptyList()

    override fun init() {
        val textWidth = (width - 40).coerceIn(1, 600)
        messageLines = font.split(message, textWidth)
        instructionLines = font.split(instruction, textWidth)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(guiGraphics, mouseX, mouseY, delta)

        val lineHeight = font.lineHeight + 3
        val paragraphGap = 16
        val textHeight = (messageLines.size + instructionLines.size) * lineHeight + paragraphGap
        var y = (height - textHeight) / 2
        for (line in messageLines) {
            guiGraphics.drawCenteredString(font, line, width / 2, y, 0xFFFFFFFF.toInt())
            y += lineHeight
        }
        y += paragraphGap
        for (line in instructionLines) {
            guiGraphics.drawCenteredString(font, line, width / 2, y, 0xFFFFFFFF.toInt())
            y += lineHeight
        }
    }

    companion object {
        fun scan(): List<File>? {
            val mods = FabricLoader.getInstance().gameDir.toFile().resolve("mods")
            if (!mods.exists() || !mods.isDirectory) return null
            return mods.listFiles { it.name.startsWith("engine") }.takeIf { it.size > 1 }?.toList()
        }
    }
}
