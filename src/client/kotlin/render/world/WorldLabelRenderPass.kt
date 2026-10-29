package org.lain.engine.client.render.world

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f
import org.lain.engine.client.render.blendPremultipliedFrom

private const val LABEL_BUFFER_SIZE = 16 * 1024

class WorldLabelRenderPass : AutoCloseable {
    private val allocator = ByteBufferBuilder(LABEL_BUFFER_SIZE)
    private val buffers = MultiBufferSource.immediate(allocator)
    private val textBuffers = LabelTextBufferSource(buffers)
    private val labels = ArrayList<Label>()
    private var target: TextureTarget? = null
    private var closed = false

    fun submit(
        matrix: Matrix4f,
        lines: List<LabelRenderState.Line>,
        textColor: Int,
        backgroundColor: Int,
        light: Int,
    ) {
        check(!closed) { "World label renderer is closed" }
        labels += Label(Matrix4f(matrix), lines, textColor, backgroundColor, light)
    }

    fun render(client: Minecraft) {
        if (labels.isEmpty()) return
        try {
            client.mainRenderTarget.blendPremultipliedFrom { renderLayer(client) }
        } finally {
            labels.clear()
        }
    }

    private fun renderLayer(client: Minecraft): TextureTarget {
        val mainTarget = client.mainRenderTarget
        val layer = target ?: TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX)
            .also {
                it.setClearColor(0f, 0f, 0f, 0f)
                target = it
            }
        if (layer.width != mainTarget.width || layer.height != mainTarget.height) {
            layer.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX)
        }

        RenderSystem.depthMask(true)
        layer.clear(Minecraft.ON_OSX)
        layer.bindWrite(true)

        // Resolve the nearest label rectangle before blending any color. Farther
        // letters cannot leak through a nearer label's translucent background.
        // Its render type biases depth away so the label passes its own mask.
        val mask = buffers.getBuffer(LabelTextRenderTypes.depthMask)
        for (label in labels) {
            label.vertices(mask, client.font.lineHeight)
        }
        buffers.endBatch()

        val background = buffers.getBuffer(LabelTextRenderTypes.background)
        for (label in labels) {
            if (label.backgroundColor ushr 24 == 0) continue
            label.vertices(background, client.font.lineHeight) {
                setColor(label.backgroundColor).setLight(label.light)
            }
        }
        buffers.endBatch()

        for (label in labels) {
            var y = 0f
            for (line in label.lines) {
                y -= client.font.lineHeight
                client.font.drawInBatch(
                    line.text,
                    -line.width / 2f,
                    y,
                    label.textColor,
                    false,
                    label.matrix,
                    textBuffers,
                    Font.DisplayMode.SEE_THROUGH,
                    0,
                    label.light,
                )
            }
        }
        buffers.endBatch()
        return layer
    }

    fun discard() {
        labels.clear()
    }

    override fun close() {
        if (closed) return
        closed = true
        labels.clear()
        target?.destroyBuffers()
        target = null
        allocator.close()
    }

    private data class Label(
        val matrix: Matrix4f,
        val lines: List<LabelRenderState.Line>,
        val textColor: Int,
        val backgroundColor: Int,
        val light: Int,
    ) {
        inline fun vertices(
            buffer: VertexConsumer,
            lineHeight: Int,
            attributes: VertexConsumer.() -> Unit = {},
        ) {
            val halfWidth = lines.maxOf { it.width } / 2f + 1f
            val bottom = -lines.size * lineHeight - 1f
            buffer.addVertex(matrix, -halfWidth, 0f, 0f).attributes()
            buffer.addVertex(matrix, halfWidth, 0f, 0f).attributes()
            buffer.addVertex(matrix, halfWidth, bottom, 0f).attributes()
            buffer.addVertex(matrix, -halfWidth, bottom, 0f).attributes()
        }
    }
}
