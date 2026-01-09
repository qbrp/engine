package org.lain.engine.client.render

import org.lain.engine.client.render.ui.Background
import org.lain.engine.client.render.ui.Composition
import org.lain.engine.client.render.ui.ConstraintsSize
import org.lain.engine.client.render.ui.EngineUi
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.Image
import org.lain.engine.client.render.ui.Layout
import org.lain.engine.client.render.ui.Size
import org.lain.engine.client.render.ui.Sizing
import org.lain.engine.client.render.ui.SpriteSizing
import org.lain.engine.client.render.ui.TextArea
import org.lain.engine.client.util.LittleNotification
import org.lain.engine.util.BLACK_TRANSPARENT_BG_COLOR
import org.lain.engine.util.clampDelta
import org.lain.engine.util.easeInStep

class LittleNotificationState(
    val info: LittleNotification,
    val width: Float,
    val height: Float,
    var x: Float,
    var y: Float,
    val composition: Composition
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
        composition.render.position.x = x
        composition.render.position.y = y
    }
}

private const val WIDTH = 175f
private const val ICON_SIZE = 16f
private fun LittleNotification(notification: LittleNotification) = Fragment(
    layout = Layout.Horizontal(4f),
    sizing = Sizing(
        ConstraintsSize.Fixed(WIDTH),
        ConstraintsSize.Wrap
    ),
    children = listOf(
        Fragment(
            sizing = Sizing(ConstraintsSize.Fixed(ICON_SIZE)),
            image = Image(
                notification.sprite,
                SpriteSizing.Stretch
            )
        ),
        Fragment(
            layout = Layout.Vertical(2f),
            children = listOfNotNull(
                Fragment(text = TextArea(notification.titleTextNode)),
                notification.descriptionTextNode?.let {
                    Fragment(text = TextArea(it, 0.7f))
                }
            )
        )
    ),
    background = Background(BLACK_TRANSPARENT_BG_COLOR)
)

class LittleNotificationsRenderManager(
    private val window: Window,
    private val ui: EngineUi
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

        toRemove.forEach {
            val state = slots.remove(it)
            state?.let { ui.removeComposition(it.composition) }
        }
    }

    fun update(dt: Float) {
        slots.values.forEach { it.update(window, dt) }
    }

    fun removeNotification(slot: String) {
        slots[slot]?.startSlideIn()
    }

    fun create(notification: LittleNotification, slot: String? = null) {
        val composition = ui.addFragment(Size(WIDTH, window.heightDp)) { LittleNotification(notification) }
        val element = composition.render

        val height = element.size.height
        element.position.y = window.heightDp - height

        slots[slot ?: generateIndex()] = LittleNotificationState(
            notification,
            WIDTH,
            height,
            window.widthDp,
            getLittleNotificationsEndY() - height,
            composition
        ).also {
            if (slot != null) {
                slots[slot] = it
            }
        }
    }

    private fun getLittleNotificationsEndY(): Float {
        var endY = window.heightDp
        littleNotifications.forEach { notification ->
            endY -= notification.height
        }
        return endY - (window.heightDp * 0.05f)
    }

    fun invalidate() {
        littleNotifications.clear()
    }

    private fun generateIndex() = lastIndex++.toString()
}
