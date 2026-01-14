package org.lain.engine.client.mc.render

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.fabricmc.fabric.impl.client.indigo.renderer.render.ItemRenderContext
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.client.gui.widget.TextWidget
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.model.BasicItemModel
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.client.render.model.json.ModelTransformation
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Colors
import net.minecraft.util.Identifier
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mixin.render.BasicItemModelAccessor
import org.lain.engine.client.render.ui.recompose
import org.lain.engine.client.resources.EngineItemModel
import org.lain.engine.util.MutableVec3
import kotlin.math.max

object AdditionalTransformationsBank {
    private val modelToTransformations: MutableMap<Identifier, Transformations> = mutableMapOf()

    fun set(model: Identifier, transformations: Transformations) {
        modelToTransformations[model] = transformations
    }

    fun remove(model: Identifier) = modelToTransformations.remove(model)

    fun get(model: Identifier) = modelToTransformations[model]
}

data class Transformations(
    val firstPersonRightHand: Transformation = Transformation.Identity(),
    val firstPersonLeftHand: Transformation = Transformation.Identity(),
    val thirdPersonRightHand: Transformation = Transformation.Identity(),
    val thirdPersonLeftHand: Transformation = Transformation.Identity(),
    val head: Transformation = Transformation.Identity(),
    val gui: Transformation = Transformation.Identity(),
    val ground: Transformation = Transformation.Identity(),
    val fixed: Transformation = Transformation.Identity(),
) {
    fun getTransformation(renderMode: ItemDisplayContext): Transformation {
        return when (renderMode) {
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND -> this.thirdPersonLeftHand
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND -> this.thirdPersonRightHand
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND -> this.firstPersonLeftHand
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND -> this.firstPersonRightHand
            ItemDisplayContext.HEAD -> this.head
            ItemDisplayContext.GUI -> this.gui
            ItemDisplayContext.GROUND -> this.ground
            ItemDisplayContext.FIXED -> this.fixed
            ItemDisplayContext.ON_SHELF -> this.fixed
            else -> Transformation.Identity()
        }
    }
}

data class Transformation(val translation: MutableVec3, val rotation: MutableVec3, val scale: MutableVec3) {
    companion object {
        fun Identity() = Transformation(MutableVec3(), MutableVec3(), MutableVec3())
    }
}

fun net.minecraft.client.render.model.json.Transformation.engine(): Transformation {
    return Transformation(translation.engine(), rotation.engine(), scale.engine())
}

fun Transformation.minecraft() = net.minecraft.client.render.model.json.Transformation(rotation.minecraft(), translation.minecraft(), scale.minecraft())

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

fun ItemRenderState.LayerRenderState.setAdditionalTransformations(transformations: Transformations, context: ItemDisplayContext) {
    setData(RENDER_STATE_KEY, transformations.minecraft().getTransformation(context))
}

fun ItemRenderState.LayerRenderState.getAdditionalTransformations(): net.minecraft.client.render.model.json.Transformation? {
    return getData(RENDER_STATE_KEY)
}

class TransformationsEditorScreen(private val itemStack: ItemStack) : Screen(Text.of("Transformation editor")) {
    private val modelId = itemStack.get(DataComponentTypes.ITEM_MODEL)!!
    private val model = MinecraftClient.bakedModelManager.getItemModel(modelId)
    private var transformations = AdditionalTransformationsBank.get(modelId) ?: computeTransformations(model)

    private fun computeTransformations(model: ItemModel): Transformations {
        val m = model as? BasicItemModel ?: (model as? EngineItemModel)?.itemModel as? BasicItemModel
        return (m as? BasicItemModelAccessor)?.`engine$getModelSettings`()?.transforms()?.let {
            Transformations(
                it.firstPersonRightHand.engine(),
                it.firstPersonLeftHand.engine(),
                it.thirdPersonRightHand.engine(),
                it.thirdPersonRightHand.engine(),
                it.head.engine(),
                it.gui.engine(),
                it.ground.engine(),
                it.fixed.engine()
            )
        }  ?: Transformations()
    }

    private var context = ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
    private val scale
        get() = transformations.getTransformation(context).scale
    private val rotation
        get() = transformations.getTransformation(context).rotation
    private val translation
        get() = transformations.getTransformation(context).translation

    private val contextList = SingleSelectionListWidget<ItemDisplayContext>(MinecraftClient, MinecraftClient.window.scaledWidth - 100 - 2, 2, 100, 150)
        .apply {
            onSelect = { index, ctx ->
                context = ctx
                this@TransformationsEditorScreen.children()
                    .filterIsInstance<TransformationSliderWidget>()
                    .forEach { it.refresh() }
            }
        }

    override fun init() {
        addDrawableChild(TransformationSliderWidget(getWidgetY(1), "Scale Z", MAX_SCALE,{ scale.z }, { scale.z = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(2), "Scale Y", MAX_SCALE,{ scale.y }, { scale.y = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(3), "Scale X", MAX_SCALE,{ scale.x }, { scale.x = it }))
        addText(4, "Scale")

        addDrawableChild(TransformationSliderWidget(getWidgetY(5), "Rotation Z", MAX_ROTATION, { rotation.z }, { rotation.z = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(6), "Rotation Y", MAX_ROTATION, { rotation.y }, { rotation.y = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(7), "Rotation X", MAX_ROTATION, { rotation.x }, { rotation.x = it }))
        addText(8, "Rotation")

        addDrawableChild(TransformationSliderWidget(getWidgetY(9), "Translation Z", MAX_TRANSLATION, { translation.z }, { translation.z = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(10), "Translation Y", MAX_TRANSLATION, { translation.y }, { translation.y = it }))
        addDrawableChild(TransformationSliderWidget(getWidgetY(11), "Translation X", MAX_TRANSLATION, { translation.x }, { translation.x = it }))
        addText(12, "Translation")

        addDrawableChild(
            ButtonWidget.builder(
                Text.of("Reset"),
                {
                    if (model as? BasicItemModel == null) return@builder
                    transformations = computeTransformations(model)
                    AdditionalTransformationsBank.set(modelId, transformations)
                }
            )
                .size(SLIDER_WIDTH, LINE_HEIGHT)
                .position(PADDING, PADDING)
                .build()
        )

        AdditionalTransformationsBank.set(modelId, transformations)
        contextList.clear()
        addDrawableChild(contextList)
        var w = 0
        for (entry in ItemDisplayContext.entries) {
            if (entry == ItemDisplayContext.NONE) continue
            val text = Text.of(
                entry.asString()
                    .replace("gui", "GUI")
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " ")
            )
            contextList.add (text, entry)
            w = max(w, MinecraftClient.textRenderer.getWidth(text) + 6)
        }
        contextList.width = w
        contextList.x = MinecraftClient.window.scaledWidth - w - 2
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
        if (model is BasicItemModel && computeTransformations(model) == transformations) {
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
    ): SliderWidget(PADDING, y, SLIDER_WIDTH, LINE_HEIGHT, Text.of(option), getter().toDouble()) {
        init { updateMessage() }

        override fun updateMessage() {
            message = Text.of("$option: " + String.format("%.2f", validatedValue()))
        }

        override fun applyValue() {
            setter(validatedValue())
        }

        fun refresh() {
            value = getter() / (2f * maxValue) + 0.5
            updateMessage()
        }

        private fun validatedValue() = ((value - 0.5f) / 0.5f * maxValue).toFloat()
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