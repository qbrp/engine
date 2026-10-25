package org.lain.engine.client.render

import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.CommonColors
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.iterate
import org.lain.engine.client.EngineClient
import org.lain.engine.client.GameSession
import org.lain.engine.client.control.togglePhotoMode
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.ui.ColorMc
import org.lain.engine.client.render.ui.InteractionProgressionRenderState
import org.lain.engine.client.render.ui.hud.MovementStatusRenderState
import org.lain.engine.client.render.ui.hud.NarrationMessageRenderState
import org.lain.engine.client.render.ui.hud.LittleNotificationsRenderManager
import org.lain.engine.client.render.ui.hud.renderMovementStatus
import org.lain.engine.client.render.ui.hud.renderNarrations
import org.lain.engine.client.render.ui.renderInteractionProgression
import org.lain.engine.client.render.world.BlockHintInspectionRenderState
import org.lain.engine.player.NarrationEvent
import org.lain.engine.player.interaction.Progression
import org.lain.engine.util.math.lerp
import kotlin.math.pow

class ScreenRenderer(private val client: EngineClient) {
    private val gameSession
        get() = client.gameSession
    private val window = client.window
    private var ticks = 0
    var hudHidden = false
    var isFirstPerson = false
    val littleNotificationsRenderer = LittleNotificationsRenderManager(window)
    val narrations = mutableListOf<NarrationMessageRenderState>()
    val interactionProgression = InteractionProgressionRenderState(0f)
    val movementStatus = MovementStatusRenderState()
    var chatOpen = false
    var blockHintInspectionRenderState: BlockHintInspectionRenderState? = null
    var zoom: Float = 1f
        private set
    private var photoModeCaption = PhotoModeCaption(Component.empty(), 0f)

    fun emitPhotoModeOnCaption(value: Boolean) {
        val text = when(value) {
            true -> Component.literal("Фоторежим включен")
            else -> Component.literal("Фоторежим выключен").withStyle(ChatFormatting.GRAY)
        }
        photoModeCaption = PhotoModeCaption(text)
    }

    fun emitPhotoModeChatBubbleCaption(value: Boolean) {
        val text = when(value) {
            true -> Component.literal("Чат-баблы включены")
            false -> Component.literal("Чат-баблы выключены").withStyle(ChatFormatting.GRAY)
        }
        photoModeCaption = PhotoModeCaption(text)
    }

    fun tick(gameSession: GameSession) {
        ticks++
        gameSession.world.iterate<NarrationEvent> { _, (playerId, narration) ->
            if (gameSession.mainPlayer.id != playerId) return@iterate
            val existingNarration = narrations.find { it.message.text == narration.text }
            if (existingNarration != null) {
                existingNarration.time = (existingNarration.time - narration.duration).coerceAtLeast(0f)
            } else {
                narrations += NarrationMessageRenderState(narration)
            }
            if (narration.kick) client.audioManager.playKickSound()
        }
        narrations.removeIf { it.time > it.message.duration }
        littleNotificationsRenderer.tick()
    }

    fun renderScreen(context: GuiGraphics, dt: Float) {
        littleNotificationsRenderer.update(dt)
        val gameSession = gameSession
        if (gameSession != null) {
            val mainPlayer = gameSession.mainPlayer
            val targetZoom = gameSession.photoMode?.zoom ?: 1f
            zoom = lerp(zoom, targetZoom, 0.02f.pow(dt))
            if (!hudHidden) {
                renderNarrations(context, narrations, dt)
                renderInteractionProgression(
                    context,
                    interactionProgression,
                    with(mainPlayer.world) { mainPlayer.entity.getComponent<Progression>() },
                    dt
                )
                littleNotificationsRenderer.render(context)
            }
            renderMovementStatus(
                context,
                movementStatus,
                gameSession,
                dt
            )
            renderPhotoModeCaption(context, dt)
        }
    }

    fun renderPhotoModeCaption(context: GuiGraphics, dt: Float) {
        val easeT = (photoModeCaption.time - 10f).coerceAtLeast(0f)
        val alpha = 1f - (easeT / 10f).coerceIn(0f, 1f)
        val alphaI = (alpha * 255).toInt()
        photoModeCaption.time += dt

        val font = MinecraftClient.font
        val textWidth = font.width(photoModeCaption.text)
        val x = context.guiWidth() / 2 - (textWidth / 2)
        val y = context.guiHeight() / 2 - 24
        if (alphaI > 8) {
            context.drawString(
                font,
                photoModeCaption.text,
                x,
                y,
                ColorMc.color(alphaI, CommonColors.WHITE)
            )
        }
    }

    fun invalidate() {
        littleNotificationsRenderer.invalidate()
        narrations.clear()
    }

    data class PhotoModeCaption(
        val text: Component,
        var time: Float = 0f
    )
}
