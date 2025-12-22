package org.lain.engine.client.render

import com.mojang.blaze3d.systems.RenderSystem
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.util.applyForEach
import org.lain.engine.util.clampDelta
import org.lain.engine.util.easeInStep

class LittleNotificationState(
    val info: LittleNotification,
    val width: Float,
    val height: Float,
    var x: Float,
    var y: Float
) {
    var endY: Float = y

    var lifetime: Float = 0f
    var slideInProgress: Float = 0f
    var slideOutProgress: Float = 0f
    var slideOut = false
    var offscreen = true

    val isExpired
        get() = lifetime >= info.lifeTime

    val isReadyCleanup
        get() = offscreen && isExpired && slideOut && slideOutProgress >= 1f

    fun startSlideIn() {
        lifetime = info.lifeTime.toFloat()
    }

    fun update(
        window: Window,
        deltaTick: Float,
    ) {
        lifetime += deltaTick

        if (!slideOut) {
            slideInProgress += deltaTick / info.transitionTime
            slideInProgress = slideInProgress.coerceIn(0f, 1f)
        }

        if (isExpired) slideOut = true

        if (slideOut) {
            slideOutProgress += deltaTick / info.transitionTime
            slideOutProgress = slideOutProgress.coerceIn(0f, 1f)
        }

        val windowWidth = window.widthDp

        val targetX = if (slideOut) window.widthDp else window.widthDp - width
        x = clampDelta(
            easeInStep(x, targetX, deltaTick),
            targetX,
            0.05f
        )
        y = easeInStep(y, endY, deltaTick)

        offscreen = x >= windowWidth - 2f
    }
}

private const val WIDTH = 175f
private const val ICON_SIZE = 16f
private val RENDER_SETTINGS = TextRenderSettings(wrap = WIDTH - ICON_SIZE)

fun FontRenderer.getLittleNotificationTextHeight(notification: LittleNotification): Float {
    var y = 0f
    y += getTextHeight(notification.titleTextNode, RENDER_SETTINGS)
    val description = notification.descriptionTextNode
    if (description != null) {
        y += getTextHeight(description, RENDER_SETTINGS)
    }
    return y
}

class LittleNotificationsRenderManager(
    private val fontRenderer: FontRenderer,
    private val window: Window
) {
    private val slots = LinkedHashMap<String, LittleNotificationState>()
    private val littleNotifications
        get() = slots.values
    private var ticks = 0
    private var lastIndex = 0

    fun tick() {
        ticks++

        val toRemove = mutableListOf<String>()
        var count = 0

        for ((key, slot) in slots) {
            count++

            if (slot.isReadyCleanup) {
                for (n in 1 until littleNotifications.size) {
                    littleNotifications.toList()[n].endY += slot.height
                }
                toRemove.add(key)
            }
        }

        toRemove.forEach { slots.remove(it) }
    }

    fun removeNotification(slot: String) {
        slots[slot]?.startSlideIn()
    }

    fun create(notification: LittleNotification, slot: String? = null) {
        val height = notification.getHeight()
        slots[slot ?: generateIndex()] = LittleNotificationState(
            notification,
            WIDTH,
            notification.getHeight(),
            window.widthDp,
            getLittleNotificationsEndY() - height
        ).also {
            if (slot != null) {
                slots[slot] = it
            }
        }
    }

    private fun LittleNotification.getHeight(): Float {
        val textHeight = fontRenderer.getLittleNotificationTextHeight(this)
        return (PADDING * 2 + textHeight).coerceAtLeast(15f)
    }

    private fun getLittleNotificationsEndY(): Float {
        var endY = window.heightDp
        littleNotifications.forEach { notification ->
            endY -= notification.height
        }
        return endY - (window.heightDp * 0.05f)
    }

    private fun renderIcon(painter: Painter, rn: LittleNotificationState, sprite: EngineSprite, x: Float, y: Float) {
        painter.push()
        painter.translate(x, y)

        RenderSystem.enableDepthTest()
        painter.fill(
            0f,
            0f,
            16f + PADDING,
            rn.height,
            color = BLACK_TRANSPARENT
        )

        painter.drawSprite(
            sprite,
            0f,
            0f,
            16f,
            16f,
            z = 100f
        )
        RenderSystem.disableDepthTest()

        painter.pop()
    }

    private fun renderNotificationText(painter: Painter, rn: LittleNotificationState, x: Float, y: Float) {
        painter.push()
        painter.translate(x, y)

        painter.fill(
            0f,
            0f,
            rn.width,
            rn.height,
            color = BLACK_TRANSPARENT
        )

        val pos = painter.drawText(
            rn.info.titleTextNode,
            PADDING,
            PADDING,
            settings = RENDER_SETTINGS
        )

        val description: EngineText? = rn.info.descriptionTextNode
        if (description != null) {
            painter.drawText(
                description,
                PADDING,
                pos.y + (PADDING / 2f),
                settings = RENDER_SETTINGS
            )
        }

        painter.pop()
    }

    fun render(painter: Painter) {
        if (window.isMinimized()) return
        ticks++
        littleNotifications.applyForEach {
            update(window, painter.tickDelta)
            val icon = info.sprite

            val startX = if (icon != null) {
                renderIcon(painter, this, icon, x, y)
                x + ICON_SIZE + PADDING
            } else {
                x
            }

            renderNotificationText(painter, this, startX, y)
        }
    }

    fun invalidate() {
        littleNotifications.clear()
    }

    private fun generateIndex() = lastIndex++.toString()

    companion object {
        private const val PADDING = 2f
    }
}
