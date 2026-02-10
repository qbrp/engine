package org.lain.engine.client.mc.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.PlayerSkinDrawer
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import org.joml.Matrix3x2f
import org.lain.engine.client.mc.injectClient
import org.lain.engine.client.render.FontRenderer
import org.lain.engine.client.render.ui.*
import org.lain.engine.util.Color
import org.lain.engine.util.injectEntityTable
import org.lain.engine.util.text.EngineOrderedText
import org.lain.engine.util.text.toMinecraft
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.max

class EngineUiRenderPipeline(
    private val client: MinecraftClient,
    private val fontRenderer: FontRenderer
) : EngineUi {
    override val elements = mutableListOf<UiElement>()
    private val textCache = TextCache()
    private val rootSize = MutableSize(0f, 0f)
    private val context by lazy { UiContext(fontRenderer, rootSize) }
    private val engine by injectClient()
    private val entityTable by injectEntityTable()
    private val alphaStack = ArrayDeque<Int>()
    var focus: Composition? = null
        private set

    private val debugBorders = LineBorders(Color.AQUA, 1f)
    private val developerMode
        get() = engine.developerMode

    override fun focusAppropriateElement(composition: Composition?): Boolean {
        val compositions = composition?.let { listOf(it) } ?: elements.map { it.composition }
        for (composition in compositions) {
            if (composition.render.handlesKeyboard) {
                focus = composition
                client.setScreen(Focus(this))
                return true
            }
            for (child in composition.children) {
                if (focusAppropriateElement(child)) {
                    return true
                }
            }
        }
        return false
    }

    override fun addFragment(clear: Boolean, focus: Boolean, fragment: () -> Fragment): Composition {
        val composition = Composition(fragment)
        recompose(composition, context)
        elements += UiElement(composition, clear)
        if (focus) focusAppropriateElement(composition)
        return composition
    }

    override fun removeComposition(composition: Composition) {
        val removed = elements.removeIf { it.composition == composition }
        if (removed && focus == composition) {
            focus = null
        }
    }

    private fun recomposeAll()  {
        elements.forEach { recompose(it.composition, context) }
    }

    fun invalidate() {
        elements.removeIf { it.clear }
        textCache.clear()
    }

    fun render(context: DrawContext, dt: Float, mouseX: Float, mouseY: Float) {
        elements.forEach {
            val pos = it.composition.render.position
            collectVertexes(it.composition, context, dt, mouseX - pos.x, mouseY - pos.y)
        }
    }

    fun collectVertexes(composition: Composition, context: DrawContext, dt: Float, mouseX: Float, mouseY: Float) {
        CompositionRenderContext.startRendering(composition, this.context)
        val state = composition.render
        state.update() //перенести
        val matrices = context.matrices
        val position = state.position
        val origin = state.origin
        val scale = state.scale
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

        val size = state.size
        val width = size.width
        val height = size.height
        val scaledWidth = size.width * scale.x
        val scaledHeight = size.height * scale.y

        val localMouseX = (mouseX + origin.x) / scale.x
        val localMouseY = (mouseY + origin.y) / scale.y

        if (state.visible) {
            val ceilWidth = ceil(width).toInt()
            val ceilHeight = ceil(height).toInt()
            if (!developerMode) {
                context.enableScissor(
                    -2, -2,
                    ceilWidth + 2,
                    ceilHeight + 2
                )
            }

            renderFeatures(
                size.width, size.height,
                ceilWidth, ceilHeight,
                state.features,
                context, dt
            )

            val borders = if (inHover(localMouseX, localMouseY, width, height) && developerMode) {
                debugBorders
            } else {
                state.borders
            }

            renderBorders(borders, width, height, context)

            for (child in composition.children) {
                context.createNewRootLayer()
                context.state.goUpLayer()
                val pos = child.render.position
                collectVertexes(child, context, dt, localMouseX - pos.x, localMouseY - pos.y)
            }

            if (!developerMode) {
                context.disableScissor()
            }
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

    fun renderBorders(borders: LineBorders, width: Float, height: Float, context: DrawContext) {
        borders.top?.let { context.fill(0f, 0f, width, it.thickness, it.color.integer) }
        borders.bottom?.let { context.fill(0f, height - it.thickness, width, height, it.color.integer) }
        borders.left?.let { context.fill(0f, 0f, it.thickness, height, it.color.integer) }
        borders.right?.let { context.fill(width, 0f, width - it.thickness, height, it.color.integer) }
    }

    fun renderListeners(
        state: UiState,
        listeners: UiListeners,
        width: Float, height: Float,
        mouseX: Float, mouseY: Float,
        context: DrawContext
    ) {
        listeners.hover?.let {
            if (inHover(mouseX, mouseY, width, height)) {
                it.invoke(state, mouseX, mouseY)
            }
        }
        listeners.render?.invoke(state)
    }

    fun inHover(mouseX: Float, mouseY: Float, width: Float, height: Float) = mouseX in 0f..width && mouseY in 0f..height

    //TODO
//    fun renderTextInput(
//        textInput: TextInputState,
//        context: DrawContext
//    ) {
//        context.drawTexturedQuad()
//    }

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
                //.withAlpha(color1.alpha / alphaStack.last() * 255)
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

        features.head?.let { head ->
            val player = entityTable.client.getEntity(head) as? ClientPlayerEntity ?: return@let
            PlayerSkinDrawer.draw(
                context,
                player.skin,
                1,
                1,
                width.toInt(),
                Color.of(80, 80, 80).integer
            )
            PlayerSkinDrawer.draw(
                context,
                player.skin,
                0,
                0,
                width.toInt(),
                Color.WHITE.integer
            )
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
    private val map = mutableMapOf<EngineOrderedText, OrderedText>()

    internal fun clear() {
        map.clear()
    }

    fun get(text: EngineOrderedText): OrderedText {
        return map.getOrPut(text) { text.toMinecraft() }
    }
}

fun keyModsSet(mods: Int): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    if (mods and GLFW.GLFW_MOD_SHIFT != 0) result.add(Modifier.SHIFT)
    if (mods and GLFW.GLFW_MOD_CONTROL != 0) result.add(Modifier.CTRL)
    if (mods and GLFW.GLFW_MOD_ALT != 0) result.add(Modifier.ALT)
    if (mods and GLFW.GLFW_MOD_SUPER != 0) result.add(Modifier.SUPER)
    if (mods and GLFW.GLFW_MOD_CAPS_LOCK != 0) result.add(Modifier.CAPS_LOCK)
    if (mods and GLFW.GLFW_MOD_NUM_LOCK != 0) result.add(Modifier.NUM_LOCK)
    return result
}

fun KeyInput.toEngineKeyEvent(action: KeyAction) = KeyEvent(key, action, keyModsSet(modifiers))

class Focus(private val ui: EngineUiRenderPipeline) : Screen(Text.of("Focus")) {
    override fun keyPressed(input: KeyInput): Boolean {
        return emitKeyEvent(
            input.toEngineKeyEvent(KeyAction.PRESS)
        )
    }

    override fun keyReleased(input: KeyInput): Boolean {
        return emitKeyEvent(
            input.toEngineKeyEvent(KeyAction.RELEASE)
        )
    }

    override fun charTyped(input: CharInput): Boolean {
        val focusedElement = ui.focus
        val renderState = focusedElement?.render
        return renderState?.onChar(CharEvent(input.codepoint, keyModsSet(input.modifiers))) ?: false
    }

    private fun emitKeyEvent(event: KeyEvent): Boolean {
        val focusedElement = ui.focus
        val renderState = focusedElement?.render
        return renderState?.onKey(event) ?: false
    }

    override fun shouldPause(): Boolean = false
    override fun renderBackground(context: DrawContext?, mouseX: Int, mouseY: Int, deltaTicks: Float) {}
}