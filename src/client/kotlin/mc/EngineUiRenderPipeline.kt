package org.lain.engine.client.mc

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.Window
import net.minecraft.text.OrderedText
import org.joml.Matrix3x2f
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.MutableVec2
import org.lain.engine.client.render.ui.EngineUi
import org.lain.engine.client.render.ui.Fragment
import org.lain.engine.client.render.ui.MutableSize
import org.lain.engine.client.render.ui.UiContext
import org.lain.engine.client.render.ui.UiElementState
import org.lain.engine.client.render.ui.UiFeatures
import org.lain.engine.client.render.ui.blend
import org.lain.engine.client.render.ui.fragmentsToUiElements
import org.lain.engine.client.render.ui.layout
import org.lain.engine.client.render.ui.measure
import org.lain.engine.util.EngineOrderedTextSequence
import org.lain.engine.util.toMinecraft
import kotlin.math.ceil

class EngineUiRenderPipeline(
    private val client: MinecraftClient,
    private val fontRenderer: FontRenderer
) : EngineUi {
    private val elements = mutableListOf<UiElementState>()
    private val textCache = TextCache()
    private val context = UiContext(fontRenderer)
    private val root by lazy { client.window.asUiElement() }

    override fun addRootFragment(fragment: Fragment): UiElementState {
        val measured = measure(context, fragment, root.size)
        val layout = layout(measured)
        val element = fragmentsToUiElements(
            context,
            layout
        )
        addRootElement(element)
        return element
    }

    override fun addRootElement(state: UiElementState) {
        elements += state
    }

    override fun removeRootElement(state: UiElementState) {
        elements -= state
    }

    fun render(context: DrawContext, dt: Float) {
        collectVertexes(root, context, dt)
    }

    fun collectVertexes(state: UiElementState, context: DrawContext, dt: Float) {
        state.update() //перенести
        val matrices = context.matrices
        val position = state.position
        val origin = state.origin
        val scale = state.scale
        val size = state.size
        matrices.pushMatrix()
        matrices.translate(position.x - origin.x, position.y - origin.y)

        if (scale != UiElementState.DEFAULT_SCALE) {
            matrices
                .translate(-origin.x, -origin.y)
                .mul(Matrix3x2f().scaling(scale.x, scale.y))
                .translate(origin.x, origin.y)
        }

        val ceilWidth = ceil(size.width).toInt()
        val ceilHeight = ceil(size.height).toInt()
        context.enableScissor(
            -1, -1,
            ceilWidth,
            ceilHeight
        )

        renderFeatures(
            size.width, size.height,
            ceilWidth, ceilHeight,
            state.features,
            context, dt
        )

        for (element in state.children) {
            context.createNewRootLayer()
            context.state.goUpLayer()
            collectVertexes(element, context, dt)
        }

        context.disableScissor()

        matrices.popMatrix()
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

        context.fill(
            0f, 0f, width, height,
            tint.blend(features.background.color1).integer,
            tint.blend(features.background.color2).integer
        )

        features.sprite?.let {
            val color = tint.blend(it.color)
            context.drawEngineSprite(it.source, 0f, 0f, width, height, color.integer)
        }

        features.text?.let { text ->
            var textY = 0
            val color = tint.blend(text.color)
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

    fun resizeRoot() = with(root.size) {
        width = client.window.scaledWidth.toFloat()
        height = client.window.scaledHeight.toFloat()
    }

    private fun Window.asUiElement(): UiElementState {
        val size = MutableSize(this.scaledWidth.toFloat(), this.scaledHeight.toFloat())
        val center = MutableVec2(size.centerX, size.centerY)
        return UiElementState(
            center,
            center,
            size,
            children = elements,
            features = UiFeatures()
        )
    }
}

class TextCache {
    private val map = mutableMapOf<EngineOrderedTextSequence, OrderedText>()

    fun get(text: EngineOrderedTextSequence): OrderedText {
        return map.getOrPut(text) { text.toMinecraft() }
    }
}