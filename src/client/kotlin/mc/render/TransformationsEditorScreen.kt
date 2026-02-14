package org.lain.engine.client.mc.render

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
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
import net.minecraft.util.Identifier
import net.minecraft.util.JsonHelper
import org.joml.Vector3f
import org.joml.Vector3fc
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mixin.render.BasicItemModelAccessor
import org.lain.engine.client.mixin.render.GameRendererAccessor
import org.lain.engine.client.mixin.render.GuiRendererAccessor
import org.lain.engine.client.resources.EngineItemModel
import org.lain.engine.util.math.MutableVec3
import java.io.File
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

fun Transformations.toDisplayJson(): JsonObject {
    val display = JsonObject()

    fun addIfNotIdentity(name: String, transformation: Transformation) {
        if (!transformation.isIdentity()) {
            display.add(name, transformation.toJson())
        }
    }

    addIfNotIdentity("thirdperson_righthand", thirdPersonRightHand)
    addIfNotIdentity("thirdperson_lefthand", thirdPersonLeftHand)
    addIfNotIdentity("firstperson_righthand", firstPersonRightHand)
    addIfNotIdentity("firstperson_lefthand", firstPersonLeftHand)
    addIfNotIdentity("head", head)
    addIfNotIdentity("gui", gui)
    addIfNotIdentity("ground", ground)
    addIfNotIdentity("fixed", fixed)

    return display
}

fun Transformation.toJson(): JsonObject {
    val json = JsonObject()

    if (!translation.isZero()) {
        json.add("translation", translation.toJsonArray(0.0625f))
    }

    if (!rotation.isZero()) {
        json.add("rotation", rotation.toJsonArray())
    }

    if (!scale.isZero()) {
        json.add("scale", scale.toJsonArray())
    }

    return json
}

fun MutableVec3.toJsonArray(divide: Float = 1f): JsonArray {
    val array = JsonArray()
    array.add(x / divide)
    array.add(y / divide)
    array.add(z / divide)
    return array
}

fun MutableVec3.isZero(): Boolean =
    x == 0f && y == 0f && z == 0f

fun Transformation.isIdentity(): Boolean =
    translation.isZero() && rotation.isZero() && scale.isZero()


fun ItemRenderState.LayerRenderState.setAdditionalTransformations(transformations: Transformations, context: ItemDisplayContext) {
    setData(RENDER_STATE_KEY, transformations.minecraft().getTransformation(context))
}

fun ItemRenderState.LayerRenderState.getAdditionalTransformations(): net.minecraft.client.render.model.json.Transformation? {
    return getData(RENDER_STATE_KEY)
}

private val guiRenderer get() = (MinecraftClient.gameRenderer as GameRendererAccessor).`engine$getGuiRenderer`() as GuiRendererAccessor


class TransformationsEditorScreen(private val itemStack: ItemStack) : Screen(Text.of("Transformation editor")) {
    private val modelId = itemStack.get(DataComponentTypes.ITEM_MODEL)!!
    private val model = MinecraftClient.bakedModelManager.getItemModel(modelId)
    private var transformations = AdditionalTransformationsBank.get(modelId) ?: computeTransformations(model)
    private val sliders = mutableListOf<TransformationSliderWidget>()

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

    fun File.withExtension(newExt: String): File {
        val nameWithoutExt = this.nameWithoutExtension.replace(".asset", "")
        val parentDir = this.parentFile
        val cleanExt = newExt.removePrefix(".")
        return File(parentDir, "$nameWithoutExt.$cleanExt")
    }

    override fun init() {
        addDrawableChild(TransformationSliderWidget(getWidgetY(1), "Scale Z", MAX_SCALE,{ scale.z }, { scale.z = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(2), "Scale Y", MAX_SCALE,{ scale.y }, { scale.y = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(3), "Scale X", MAX_SCALE,{ scale.x }, { scale.x = it }, model))
        addText(4, "Scale")

        addDrawableChild(TransformationSliderWidget(getWidgetY(5), "Rotation Z", MAX_ROTATION, { rotation.z }, { rotation.z = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(6), "Rotation Y", MAX_ROTATION, { rotation.y }, { rotation.y = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(7), "Rotation X", MAX_ROTATION, { rotation.x }, { rotation.x = it }, model))
        addText(8, "Rotation")

        addDrawableChild(TransformationSliderWidget(getWidgetY(9), "Translation Z", MAX_TRANSLATION, { translation.z }, { translation.z = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(10), "Translation Y", MAX_TRANSLATION, { translation.y }, { translation.y = it }, model))
        addDrawableChild(TransformationSliderWidget(getWidgetY(11), "Translation X", MAX_TRANSLATION, { translation.x }, { translation.x = it }, model))
        addText(12, "Translation")

        addDrawableChild(
            ButtonWidget.builder(
                Text.of("Reset"),
                {
                    if (model == null) return@builder
                    transformations = computeTransformations(model)
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
                val source = model.asset.source.file
                    .withExtension("json")
                val reader = source.reader()
                val json = JsonHelper.deserialize(reader).asJsonObject
                json.add(
                    "display",
                    transformations.toDisplayJson()
                )
                reader.close()
                source.writeText(Gson().toJson(json))
                AdditionalTransformationsBank.remove(modelId)
            }
                .size(SLIDER_WIDTH, LINE_HEIGHT)
                .position(PADDING, PADDING + LINE_HEIGHT)
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