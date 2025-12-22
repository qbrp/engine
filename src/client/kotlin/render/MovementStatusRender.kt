package org.lain.engine.client.render

import org.lain.engine.client.EngineClient
import org.lain.engine.util.clampDelta
import org.lain.engine.util.lerp
import kotlin.math.abs
import kotlin.math.pow

class MovementStatusRenderer(
    private val window: Window,
    private val client: EngineClient
) {
    private val stamina = ArcStatus(0, STAMINA_COLOR, value = 1f)
    private val intention = ArcStatus(1, SPEED_COLOR, value = 0.5f)
    private val arcs = listOf(stamina, intention)

    private val activeArcs
        get() = arcs.filter { it.value > 0.01f && it.opacity > 0.01f }
    private val arcRadius
        get() = client.options.arcRadius.get()
    private val arcThickness
        get() = client.options.arcThickness.get()
    private val offsetX
        get() = client.options.arcOffsetX.get()
    private val offsetY
        get() = client.options.arcOffsetY.get()

    data class ArcStatus(
        val index: Int,
        val color: Int,
        var value: Float = 0f,
        var opacity: Float = 0f,
        var lastUpdate: Float = 0f,
        var startAngle: Float = 0f,
        var endAngle: Float = 0f,
    )

    fun renderArc(
        arc: ArcStatus,
        painter: Painter,
        value: Float,
        centerX: Float,
        centerY: Float,
        deltaTick: Float,
        t: Float,
    ) {
        val curValue = arc.value
        arc.value = lerp(curValue, value, t)
        val lastUpdate = arc.lastUpdate

        val isUpdating = abs(curValue - value) > 0.003f
        if (isUpdating) {
            arc.lastUpdate = 0.0f
        }

        val targetAlpha = if (lastUpdate < 40f) {
            1f
        } else {
            0f
        }

        val active = activeArcs
        val count = active.size.coerceAtLeast(1)

        val slot = 360f / count

        val targetStartAngle = arc.index * slot
        val targetEndAngle = targetStartAngle + slot
        arc.startAngle = lerp(arc.startAngle, targetStartAngle, t)
        arc.endAngle = lerp(arc.endAngle, targetEndAngle, t)

        val color = arc.color.withAlpha(arc.opacity)

        painter.drawArc(
            centerX,
            centerY,
            arcRadius,
            arcThickness,
            color.withDarken(0.5f),
            1f,
            startAngle = arc.startAngle,
            endAngle = arc.endAngle
        )

        painter.drawArc(
            centerX,
            centerY,
            arcRadius,
            arcThickness,
            color,
            curValue,
            startAngle = arc.startAngle,
            endAngle = arc.endAngle
        )

        arc.opacity = clampDelta(
            lerp(arc.opacity, targetAlpha, t),
            targetAlpha,
            0.005f
        )

        arc.lastUpdate += deltaTick
    }

    fun render(
        painter: Painter,
        intention: Float,
        stamina: Float,
        deltaTick: Float,
    ) {
        val t = 1 - 0.8f.pow(deltaTick)

        val centerX = window.widthDp / 2f + offsetX
        val centerY = window.heightDp / 2f + offsetY

        renderArc(this.stamina, painter, stamina, centerX, centerY, deltaTick, t)
        renderArc(this.intention, painter, intention, centerX, centerY, deltaTick, t)
    }
}