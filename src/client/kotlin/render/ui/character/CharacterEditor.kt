package org.lain.engine.client.render.ui.character

import imgui.ImGui
import imgui.type.ImInt
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Component
import org.lain.engine.client.GameSession
import org.lain.engine.client.render.ui.ImGuiWindow
import org.lain.engine.player.character.CharacterData
import java.util.*

class CharacterEditorScreen(
    private val gameSession: GameSession,
    private val level: ClientLevel,
    private val character: CharacterData,
) : Screen(Component.literal("Редактор персонажей")) {
    private val imGuiManager = gameSession.client.imGuiManager

    private val propertiesWindow = PropertiesWindow()

    private var lookIndex = 0
    private val looks get() = character.looks
    private val look
        get() = looks.getOrNull(lookIndex.coerceIn(looks.indices))
            ?: character.baseLook
    private val characterPreview = CharacterPreviewWidget(
        createCharacterPreviewPlayer(
            level,
            character.profile,
            UUID.nameUUIDFromBytes("edit-character".toByteArray()),
        ) { gameSession.client.skinTextureManager.getOrDownloadTexture(look) },
        character.profile
    )

    inner class PropertiesWindow : ImGuiWindow {
        override val title: String = "Параметры"
        override fun render() {
            val imLookIndex = ImInt(lookIndex)
            ImGui.listBox("Облик", imLookIndex, looks.map { it.title }.toTypedArray())
            lookIndex = imLookIndex.get()
        }
    }

    override fun render(guiGraphics: GuiGraphics, i: Int, j: Int, f: Float) {
        super.render(guiGraphics, i, j, f)
        characterPreview.computeScale(2.5f)
        characterPreview.computePosition(width / 2f, height - 25f)
        characterPreview.applyRotation(180f, 0f)
        characterPreview.render(guiGraphics)
    }

    override fun init() {
        imGuiManager.addWindow(propertiesWindow)
    }

    override fun onClose() {
        super.onClose()
        imGuiManager.close(propertiesWindow)
    }
}