package org.lain.engine.client.mc.render

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.model.BasicItemModel
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lain.engine.client.mc.ClientMixinAccess
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.render.AdditionalTransformationsBank.get
import org.lain.engine.client.mixin.render.BasicItemModelAccessor
import org.lain.engine.client.mixin.render.GameRendererAccessor
import org.lain.engine.client.mixin.render.GuiRendererAccessor
import org.lain.engine.client.mixin.render.ItemRenderStateAccessor
import org.lain.engine.client.resources.EngineItemModel
import org.lain.engine.client.resources.exportEngineModelTransformations
import org.lain.engine.util.math.MutableVec3
import kotlin.math.max

object AdditionalTransformationsBank {
    private val modelToTransformations: MutableMap<Identifier, Transformations> = mutableMapOf()
    var clipboardSingleTransform: EngineTransformation? = null
    var clipboardFullTransform: Transformations? = null
    var lastDisplayContext: EngineItemDisplayContext? = null

    fun set(model: Identifier, transformations: Transformations) {
        modelToTransformations[model] = transformations
    }

    fun remove(model: Identifier) = modelToTransformations.remove(model)

    fun get(model: Identifier) = modelToTransformations[model]
}

enum class EngineItemDisplayContext(val minecraft: ItemDisplayContext) {
    NONE(ItemDisplayContext.NONE),
    THIRD_PERSON_LEFT_HAND(ItemDisplayContext.THIRD_PERSON_LEFT_HAND),
    THIRD_PERSON_RIGHT_HAND(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND),
    FIRST_PERSON_LEFT_HAND(ItemDisplayContext.FIRST_PERSON_LEFT_HAND),
    FIRST_PERSON_RIGHT_HAND(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND),
    HEAD(ItemDisplayContext.HEAD),
    GUI(ItemDisplayContext.GUI),
    GROUND(ItemDisplayContext.GROUND),
    FIXED(ItemDisplayContext.FIXED),
    OUTFIT(ItemDisplayContext.NONE)
}

data class Transformations(
    val firstPersonRightHand: EngineTransformation = EngineTransformation.Identity(),
    val firstPersonLeftHand: EngineTransformation = EngineTransformation.Identity(),
    val thirdPersonRightHand: EngineTransformation = EngineTransformation.Identity(),
    val thirdPersonLeftHand: EngineTransformation = EngineTransformation.Identity(),
    val head: EngineTransformation = EngineTransformation.Identity(),
    val gui: EngineTransformation = EngineTransformation.Identity(),
    val ground: EngineTransformation = EngineTransformation.Identity(),
    val fixed: EngineTransformation = EngineTransformation.Identity(),
    val outfit: EngineTransformation = EngineTransformation.Identity(),
) {
    fun getTransformation(renderMode: EngineItemDisplayContext): EngineTransformation {
        return when (renderMode) {
            EngineItemDisplayContext.THIRD_PERSON_LEFT_HAND -> this.thirdPersonLeftHand
            EngineItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> this.thirdPersonRightHand
            EngineItemDisplayContext.FIRST_PERSON_LEFT_HAND -> this.firstPersonLeftHand
            EngineItemDisplayContext.FIRST_PERSON_RIGHT_HAND -> this.firstPersonRightHand
            EngineItemDisplayContext.HEAD -> this.head
            EngineItemDisplayContext.GUI -> this.gui
            EngineItemDisplayContext.GROUND -> this.ground
            EngineItemDisplayContext.FIXED -> this.fixed
            EngineItemDisplayContext.OUTFIT -> this.outfit
            else -> EngineTransformation.Identity()
        }
    }
}

data class EngineTransformation(
    val translation: MutableVec3,
    val rotation: MutableVec3,
    val scale: MutableVec3
) {
    companion object {
        fun Identity() = EngineTransformation(
            MutableVec3(0f, 0f, 0f),
            MutableVec3(0f, 0f, 0f),
            MutableVec3(1f, 1f, 1f)
        )
    }
}

fun net.minecraft.client.render.model.json.Transformation.engine(): EngineTransformation {
    return EngineTransformation(translation.engine(), rotation.engine(), scale.engine())
}

fun EngineTransformation.minecraft() = net.minecraft.client.render.model.json.Transformation(rotation.minecraft(), translation.minecraft(), scale.minecraft())

private fun Vector3fc.engine() = MutableVec3(x(), y(), z())

private fun MutableVec3.minecraft(): Vector3fc = Vector3f(x, y, z)

private val RENDER_STATE_KEY = RenderStateDataKey.create<net.minecraft.client.render.model.json.Transformation> { "Engine Additional Transformations" }

fun Transformations.minecraft() = ModelTransformation(
    thirdPersonLeftHand.minecraft(),
    thirdPersonRightHand.minecraft(),
    firstPersonLeftHand.minecraft(),
    firstPersonRightHand.minecraft(),
    head.minecraft(),
    gui.minecraft(),
    ground.minecraft(),
    fixed.minecraft(),
    fixed.minecraft(), // ???
)

fun MutableVec3.isZero(): Boolean =
    x == 0f && y == 0f && z == 0f

fun EngineTransformation.isIdentity(): Boolean =
    translation.isZero() && rotation.isZero() && scale.isZero()


fun ItemRenderState.LayerRenderState.setAdditionalTransformationsVanillaPipeline(transformations: Transformations, context: ItemDisplayContext) {
    setData(RENDER_STATE_KEY, transformations.minecraft().getTransformation(context))
}

fun ItemRenderState.LayerRenderState.setAdditionalTransformationsEnginePipeline(transformations: Transformations, context: EngineItemDisplayContext) {
    setData(RENDER_STATE_KEY, transformations.getTransformation(context).minecraft())
}

fun ItemRenderState.LayerRenderState.getAdditionalTransformations(): net.minecraft.client.render.model.json.Transformation? {
    return getData(RENDER_STATE_KEY)
}

fun ItemRenderState.setupAdditionalTransformationsVanilla(stack: ItemStack, displayContext: ItemDisplayContext) {
    val transformations = get(stack.get(DataComponentTypes.ITEM_MODEL)!!)
    if (transformations != null) {
        val layers = (this as ItemRenderStateAccessor).`engine$getLayers`()
        for (layer in layers) {
            layer.setAdditionalTransformationsVanillaPipeline(transformations, displayContext)
        }
    }
}

fun ItemRenderState.setupAdditionalTransformationsEngine(stack: ItemStack, displayContext: EngineItemDisplayContext) {
    val transformations = get(stack.get(DataComponentTypes.ITEM_MODEL)!!)
    if (transformations != null) {
        val layers = (this as ItemRenderStateAccessor).`engine$getLayers`()
        for (layer in layers) {
            layer.setAdditionalTransformationsEnginePipeline(transformations, displayContext)
        }
    }
}

private val guiRenderer get() = (MinecraftClient.gameRenderer as GameRendererAccessor).`engine$getGuiRenderer`() as GuiRendererAccessor

class TransformationsEditorScreen(private val itemStack: ItemStack) : Screen(Text.of("Transformation editor")) {
    private val modelId = ClientMixinAccess.getEngineItemModel(itemStack) ?: itemStack.get(DataComponentTypes.ITEM_MODEL)!!
    private val model = MinecraftClient.bakedModelManager.getItemModel(modelId)
    private var transformations = get(modelId) ?: computeTransformations(model)
    private val sliders
        get() = this@TransformationsEditorScreen.children().filterIsInstance<TransformationSliderWidget>()

    private fun computeTransformations(model: ItemModel): Transformations {
        val engineModel = model as? EngineItemModel
        val basicModel = (model as? BasicItemModel) ?: engineModel?.itemModel as? BasicItemModel

        return (basicModel as? BasicItemModelAccessor)?.`engine$getModelSettings`()?.transforms()?.let {
            Transformations(
                it.firstPersonRightHand.engine(),
                it.firstPersonLeftHand.engine(),
                it.thirdPersonRightHand.engine(),
                it.thirdPersonRightHand.engine(),
                it.head.engine(),
                it.gui.engine(),
                it.ground.engine(),
                it.fixed.engine(),
                engineModel?.outfitTransformation?.engine() ?: EngineTransformation.Identity(),
            )
        }  ?: Transformations()
    }

    private var context = AdditionalTransformationsBank.lastDisplayContext ?: EngineItemDisplayContext.FIRST_PERSON_RIGHT_HAND
    private val scale
        get() = transformations.getTransformation(context).scale
    private val rotation
        get() = transformations.getTransformation(context).rotation
    private val translation
        get() = transformations.getTransformation(context).translation

    private val contextList = SingleSelectionListWidget<EngineItemDisplayContext>(MinecraftClient, MinecraftClient.window.scaledWidth - 100 - 2, 2, 100, 150)
        .apply {
            onSelect = { index, ctx ->
                context = ctx
                AdditionalTransformationsBank.lastDisplayContext = ctx
                sliders.forEach { it.refresh() }
            }
        }

    fun addSliderWithField(i: Int, option: String, maxValue: Float, getter: () -> Float, setter: (Float) -> Unit) {
        val slider = TransformationSliderWidget(
            getWidgetY(i),
            option,
            maxValue,
            getter,
            setter,
            model
        )
        addDrawableChild(slider)
        val textField = TransformationTextFieldWidget(getWidgetY(i))
        textField.setChangedListener { _ ->
            val text = textField.text
            if (text.isNotEmpty()) {
                runCatching { text.toFloat() }
                    .onFailure { it.printStackTrace() }
                    .onSuccess {
                        slider.updateValue(it)
                    }
            }
        }
        textField.setMaxLength(16)
        addDrawableChild(textField)
    }

    fun addClipboardButton(text: String, callback: String, x: Int, y: Int, setter: () -> Boolean) {
        val text = Text.of(text)
        addDrawableChild(
            ButtonWidget.builder(text) { button ->
                if (model == null) return@builder
                val results = setter()
                if (!results) return@builder
                button.message = Text.of(callback)
                CoroutineScope(Dispatchers.Default).launch {
                    delay(500)
                    client?.execute { button.message = text }
                }
            }
                .size(SLIDER_WIDTH, LINE_HEIGHT)
                .position(x, y)
                .build()
        )
    }

    override fun init() {
        addSliderWithField(1, "Scale Z", MAX_SCALE,{ scale.z }, { scale.z = it })
        addSliderWithField(2, "Scale Y", MAX_SCALE,{ scale.y }, { scale.y = it })
        addSliderWithField(3, "Scale X", MAX_SCALE,{ scale.x }, { scale.x = it })
        addText(4, "Scale")

        addSliderWithField(5, "Rotation Z", MAX_ROTATION, { rotation.z }, { rotation.z = it })
        addSliderWithField(6, "Rotation Y", MAX_ROTATION, { rotation.y }, { rotation.y = it })
        addSliderWithField(7, "Rotation X", MAX_ROTATION, { rotation.x }, { rotation.x = it })
        addText(8, "Rotation")

        addSliderWithField(9, "Translation Z", MAX_TRANSLATION, { translation.z }, { translation.z = it })
        addSliderWithField(10, "Translation Y", MAX_TRANSLATION, { translation.y }, { translation.y = it })
        addSliderWithField(11, "Translation X", MAX_TRANSLATION, { translation.x }, { translation.x = it })
        addText(12, "Translation")

        addDrawableChild(
            ButtonWidget.builder(
                Text.of("Reset"),
                {
                    if (model == null) return@builder
                    transformations = computeTransformations(model)
                    sliders.forEach { it.refresh() }
                    AdditionalTransformationsBank.set(modelId, transformations)
                }
            )
                .size(SLIDER_WIDTH, LINE_HEIGHT)
                .position(PADDING, PADDING)
                .build()
        )

        addDrawableChild(
            ButtonWidget.builder(
                Text.of("Export engine model")
            ) {
                if (model as? EngineItemModel == null) return@builder
                exportEngineModelTransformations(model, transformations)
                AdditionalTransformationsBank.remove(modelId)
            }
                .size(SLIDER_WIDTH, LINE_HEIGHT)
                .position(PADDING, PADDING + LINE_HEIGHT)
                .build()
        )

        addClipboardButton("Copy partial", "Copied!", PADDING + SLIDER_WIDTH, PADDING) {
            AdditionalTransformationsBank.clipboardSingleTransform = EngineTransformation(translation, rotation, scale)
            true
        }

        addClipboardButton("Copy full", "Copied!", PADDING + SLIDER_WIDTH, PADDING + LINE_HEIGHT) {
            AdditionalTransformationsBank.clipboardFullTransform = transformations.copy()
            true
        }

        addClipboardButton("Paste partial", "Pasted!", PADDING + SLIDER_WIDTH * 2, PADDING) {
            val transform = AdditionalTransformationsBank.clipboardSingleTransform ?: return@addClipboardButton false
            translation.set(transform.translation)
            rotation.set(transform.rotation)
            scale.set(transform.scale)
            sliders.forEach { it.refresh() }
            true
        }

        addClipboardButton("Paste full", "Pasted!", PADDING + SLIDER_WIDTH * 2, PADDING + LINE_HEIGHT) {
            transformations = AdditionalTransformationsBank.clipboardFullTransform?.copy() ?: return@addClipboardButton false
            sliders.forEach { it.refresh() }
            true
        }

        AdditionalTransformationsBank.set(modelId, transformations)
        contextList.clear()
        addDrawableChild(contextList)
        var w = 0

        fun addDisplayContext(text: String, context: EngineItemDisplayContext) {
            val text = Text.of(
                text
                    .lowercase()
                    .replace("gui", "GUI")
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " ")
            )
            contextList.add(text, context)
            w = max(w, MinecraftClient.textRenderer.getWidth(text) + 6)
        }

        for (entry in EngineItemDisplayContext.entries) {
            if (entry == EngineItemDisplayContext.NONE) continue
            addDisplayContext(entry.name, entry)
        }

        contextList.width = w
        contextList.x = MinecraftClient.window.scaledWidth - w - 2
        contextList.setSelectedByValue(context)
    }

    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        super.mouseClicked(click, doubled)
        return contextList.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        return contextList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun close() {
        super.close()
        if (computeTransformations(model) == transformations) {
            AdditionalTransformationsBank.remove(modelId)
        }
    }

    override fun shouldPause(): Boolean {
        return false
    }

    override fun renderBackground(context: DrawContext?, mouseX: Int, mouseY: Int, deltaTicks: Float) {}

    private fun addText(i: Int, text: String) {
        val client = MinecraftClient
        addDrawableChild(TextWidget(PADDING, getWidgetY(i) + client.textRenderer.fontHeight - 1, client.window.scaledWidth, client.textRenderer.fontHeight, Text.of(text), client.textRenderer))
    }

    private fun getWidgetY(i: Int) = MinecraftClient.window.scaledHeight - (LINE_HEIGHT + PADDING) * i


    private class TransformationSliderWidget(
        y: Int,
        private val option: String,
        val maxValue: Float,
        private val getter: () -> Float,
        private val setter: (Float) -> Unit,
        private val model: ItemModel,
    ): SliderWidget(PADDING, y, SLIDER_WIDTH, LINE_HEIGHT, Text.of(option), getter().toDouble()) {
        private var tick = 0
        init { refresh() }

        override fun updateMessage() {
            message = Text.of("$option: " + String.format("%.2f", validatedValue()))
        }

        override fun applyValue() {
            setter(validatedValue())
            if (tick++ % 10 == 0) {
                (model as? EngineItemModel).let {
                    guiRenderer.`engine$onItemAtlasChanged`()
                }
            }
        }

        fun refresh() {
            value = getter() / (2f * maxValue) + 0.5
            updateMessage()
        }

        fun updateValue(value: Float) {
            this.value = value / (2f * maxValue) + 0.5
            applyValue()
            updateMessage()
        }

        private fun validatedValue() = ((value - 0.5f) / 0.5f * maxValue).toFloat()
    }

    private class TransformationTextFieldWidget(y: Int) : TextFieldWidget(MinecraftClient.textRenderer, PADDING * 2 + SLIDER_WIDTH, y, 60, LINE_HEIGHT, Text.empty()) {
        override fun getText(): String {
            return super.getText().replace(Regex("[^0-9.]"), "")
        }
    }

    companion object {
        private const val LINE_HEIGHT = 15
        private const val SLIDER_WIDTH = 160
        private const val PADDING = 2
        private const val MAX_TRANSLATION = 3f
        private const val MAX_SCALE = 3f
        private const val MAX_ROTATION = 180f
    }
}