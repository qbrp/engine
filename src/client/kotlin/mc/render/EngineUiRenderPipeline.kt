package org.lain.engine.client.mc.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.OrderedText
import org.joml.Matrix3x2f
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.ui.Composition
import org.lain.engine.client.render.ui.CompositionRenderContext
import org.lain.engine.client.render.ui.EngineUi
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.MutableSize
import org.lain.engine.client.render.ui.Size
import org.lain.engine.client.render.ui.UiContext
import org.lain.engine.client.render.ui.UiState
import org.lain.engine.client.render.ui.UiFeatures
import org.lain.engine.client.render.ui.UiListeners
import org.lain.engine.client.render.ui.blend
import org.lain.engine.client.render.ui.recompose
import org.lain.engine.util.Color
import org.lain.engine.util.text.EngineOrderedTextSequence
import org.lain.engine.util.text.toMinecraft
import kotlin.math.ceil
import kotlin.math.max

class EngineUiRenderPipeline(
    private val client: MinecraftClient,
    private val fontRenderer: FontRenderer
) : EngineUi {
    private val elements = mutableListOf<Slot>()
    private val textCache = TextCache()
    private val rootSize = MutableSize(0f, 0f)
    private val context by lazy { UiContext(fontRenderer, rootSize) }
    private val alphaStack = ArrayDeque<Int>()

    private data class Slot(val composition: Composition, val clear: Boolean)

    override fun addFragment(clear: Boolean, fragment: () -> Fragment): Composition {
        val composition = Composition(fragment, context)
        elements += Slot(composition, clear)
        return composition
    }

    override fun removeComposition(composition: Composition) {
        elements.removeIf { it.composition == composition }
    }

    private fun recomposeAll()  {
        elements.forEach { recompose(it.composition, context) }
    }

    fun invalidate() {
        elements.removeIf { it.clear }
        textCache.clear()
    }

    fun render(context: DrawContext, dt: Float, mouseX: Float, mouseY: Float) {
        elements.forEach { collectVertexes(it.composition, context, dt, mouseX, mouseY) }
    }

    fun collectVertexes(composition: Composition, context: DrawContext, dt: Float, mouseX: Float, mouseY: Float) {
        CompositionRenderContext.uiContext = this.context
        CompositionRenderContext.composition = composition
        val state = composition.render
        state.update() //перенести
        val matrices = context.matrices
        val position = state.position
        val origin = state.origin
        val scale = state.scale
        val size = state.size
        val opacity = max(0, 255 - (state.opacity - (alphaStack.lastOrNull() ?: 255)))
        alphaStack.addLast(opacity)
        matrices.pushMatrix()
        matrices.translate(position.x - origin.x, position.y - origin.y)

        if (scale != UiState.DEFAULT_SCALE) {
            matrices
                .translate(-origin.x, -origin.y)
                .mul(Matrix3x2f().scaling(scale.x, scale.y))
                .translate(origin.x, origin.y)
        }

        if (state.visible) {
            val ceilWidth = ceil(size.width).toInt()
            val ceilHeight = ceil(size.height).toInt()
//            context.enableScissor(
//                -2, -2,
//                ceilWidth + 2,
//                ceilHeight + 2
//            )

            renderFeatures(
                size.width, size.height,
                ceilWidth, ceilHeight,
                state.features,
                context, dt
            )

            for (child in composition.children) {
                context.createNewRootLayer()
                context.state.goUpLayer()
                collectVertexes(child, context, dt, mouseX - position.x, mouseY - position.y)
            }

//            context.disableScissor()
        }

        renderListeners(
            state,
            state.listeners,
            size.width, size.height,
            mouseX, mouseY,
            context
        )

        matrices.popMatrix()
        alphaStack.removeLast()
    }

    fun renderListeners(
        state: UiState,
        listeners: UiListeners,
        width: Float, height: Float,
        mouseX: Float, mouseY: Float,
        context: DrawContext
    ) {
        listeners.hover?.let {
            if (mouseX in 0f..width && mouseY in 0f..height) {
                it.invoke(state, mouseX, mouseY)
            }
        }
        if (listeners.render != null) {
            listeners.render?.invoke(state)
        }
    }

    fun renderFeatures(
        width: Float, height: Float,
        ceilWidth: Int, ceilHeight: Int,
        features: UiFeatures,
        context: DrawContext,
        dt: Float
    ) {
        context.matrices.pushMatrix()
        val tint = features.tint

        fun getColor(color: Color): Color {
            return tint
                .blend(color)
                //.withAlpha(color.alpha / alphaStack.last() * 255)
        }

        context.fill(
            0f, 0f, width, height,
            getColor(features.background.color1).integer,
            getColor(features.background.color2).integer
        )

        features.sprite?.let {
            val color = getColor(it.color)
            context.drawEngineSprite(it.source, 0f, 0f, width, height, color)
        }

        features.text?.let { text ->
            var textY = 0
            val color = getColor(text.color)
            context.matrices.scale(text.scale)
            text.lines.forEach { line ->
                context.drawTextWithShadow(
                    client.textRenderer,
                    textCache.get(line),
                    0,
                    textY,
                    color.integer
                )
                textY += client.textRenderer.fontHeight
            }
        }
        context.matrices.popMatrix()
    }

    fun resizeRoot() = with(rootSize) {
        width = client.window.scaledWidth.toFloat()
        height = client.window.scaledHeight.toFloat()
        recomposeAll()
    }
}

class TextCache {
    private val map = mutableMapOf<EngineOrderedTextSequence, OrderedText>()

    internal fun clear() {
        map.clear()
    }

    fun get(text: EngineOrderedTextSequence): OrderedText {
        return map.getOrPut(text) { text.toMinecraft() }
    }
}