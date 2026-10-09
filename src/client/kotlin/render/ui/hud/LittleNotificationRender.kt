package org.lain.engine.client.render.ui.hud

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.util.FormattedCharSequence
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.LittleNotification
import org.lain.engine.client.render.Window
import org.lain.engine.client.render.ui.descriptionText
import org.lain.engine.client.render.ui.titleText
import org.lain.engine.mc.engineId
import org.lain.engine.util.BLACK_TRANSPARENT_BG_COLOR
import org.lain.engine.util.Color
import org.lain.engine.util.math.clampDelta
import org.lain.engine.util.math.easeInStep
import kotlin.math.ceil
import kotlin.math.max

private const val NOTIFICATION_WIDTH = 175
private const val ICON_SIZE = 16
private const val CONTENT_GAP = 4
private const val TEXT_WIDTH = NOTIFICATION_WIDTH - ICON_SIZE - CONTENT_GAP
private const val TEXT_GAP = 2
private const val DESCRIPTION_SCALE = 0.7f

private data class LittleNotificationLayout(
    val title: List<FormattedCharSequence>,
    val description: List<FormattedCharSequence>,
    val height: Int,
)

private class LittleNotificationState(
    val info: LittleNotification,
    val layout: LittleNotificationLayout,
    var x: Float,
    var y: Float,
) {
    var lifetime = 0f
    var slideOutProgress = 0f
    var slideOut = false
    var offscreen = true

    private val isExpired
        get() = lifetime >= info.lifeTime

    val isReadyCleanup
        get() = offscreen && slideOut && slideOutProgress >= 1f

    fun startSlideOut() {
        lifetime = info.lifeTime.toFloat()
    }

    fun update(windowWidth: Float, targetY: Float, deltaTick: Float) {
        lifetime += deltaTick
        if (isExpired) slideOut = true

        if (slideOut) {
            slideOutProgress = (slideOutProgress + deltaTick / info.transitionTime).coerceIn(0f, 1f)
        }

        val targetX = if (slideOut) windowWidth else windowWidth - NOTIFICATION_WIDTH
        x = clampDelta(
            easeInStep(x, targetX, deltaTick),
            targetX,
            0.05f,
        )
        y = easeInStep(y, targetY, deltaTick)
        offscreen = x >= windowWidth - 2f
    }
}

class LittleNotificationsRenderManager(
    private val window: Window,
) {
    private val slots = LinkedHashMap<String, LittleNotificationState>()
    private var lastIndex = 0

    fun tick() {
        slots.entries.removeIf { it.value.isReadyCleanup }
    }

    fun update(deltaTick: Float) {
        var targetY = window.heightDp - window.heightDp * 0.05f
        slots.values.forEach { state ->
            targetY -= state.layout.height
            state.update(window.widthDp, targetY, deltaTick)
        }
    }

    fun render(context: GuiGraphics) {
        slots.values.forEach { state -> render(context, state) }
    }

    fun removeNotification(slot: String) {
        slots[slot]?.startSlideOut()
    }

    fun create(notification: LittleNotification, slot: String? = null) {
        val key = slot ?: generateIndex()
        val state = LittleNotificationState(
            info = notification,
            layout = createLayout(notification),
            x = window.widthDp,
            y = 0f,
        )
        slots[key] = state
        state.y = targetY(state)
    }

    fun invalidate() {
        slots.clear()
    }

    private fun createLayout(notification: LittleNotification): LittleNotificationLayout {
        val font = MinecraftClient.font
        val title = font.split(notification.titleText, TEXT_WIDTH)
        val description = notification.descriptionText?.let {
            font.split(it, ceil(TEXT_WIDTH / DESCRIPTION_SCALE).toInt())
        }.orEmpty()
        val titleHeight = title.size * font.lineHeight
        val descriptionHeight = ceil(description.size * font.lineHeight * DESCRIPTION_SCALE).toInt()
        val textHeight = titleHeight + if (description.isEmpty()) 0 else TEXT_GAP + descriptionHeight

        return LittleNotificationLayout(
            title = title,
            description = description,
            height = max(ICON_SIZE, textHeight),
        )
    }

    private fun targetY(target: LittleNotificationState): Float {
        var targetY = window.heightDp - window.heightDp * 0.05f
        for (state in slots.values) {
            targetY -= state.layout.height
            if (state === target) return targetY
        }
        return targetY
    }

    private fun render(context: GuiGraphics, state: LittleNotificationState) {
        val font = MinecraftClient.font
        val pose = context.pose()
        pose.pushPose()
        pose.translate(state.x.toDouble(), state.y.toDouble(), 0.0)

        context.fill(0, 0, NOTIFICATION_WIDTH, state.layout.height, BLACK_TRANSPARENT_BG_COLOR.integer)
        val sprite = state.info.sprite
        val sourceWidth = ((sprite.u2 - sprite.u1) * sprite.resolution).toInt()
        val sourceHeight = ((sprite.v2 - sprite.v1) * sprite.resolution).toInt()
        context.blit(
            engineId(sprite.path),
            0,
            0,
            ICON_SIZE,
            ICON_SIZE,
            sprite.u1 * sprite.resolution,
            sprite.v1 * sprite.resolution,
            sourceWidth,
            sourceHeight,
            sprite.resolution,
            sprite.resolution,
        )

        val textX = ICON_SIZE + CONTENT_GAP
        state.layout.title.forEachIndexed { index, line ->
            context.drawString(font, line, textX, index * font.lineHeight, Color.WHITE.integer)
        }

        if (state.layout.description.isNotEmpty()) {
            pose.pushPose()
            pose.translate(
                textX.toDouble(),
                (state.layout.title.size * font.lineHeight + TEXT_GAP).toDouble(),
                0.0,
            )
            pose.scale(DESCRIPTION_SCALE, DESCRIPTION_SCALE, 1f)
            state.layout.description.forEachIndexed { index, line ->
                context.drawString(font, line, 0, index * font.lineHeight, Color.WHITE.integer)
            }
            pose.popPose()
        }

        pose.popPose()
    }

    private fun generateIndex() = lastIndex++.toString()
}
