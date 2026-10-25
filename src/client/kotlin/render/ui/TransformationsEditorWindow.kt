package org.lain.engine.client.render.ui

import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Mode
import imgui.extension.imguizmo.flag.Operation
import imgui.type.ImFloat
import net.minecraft.client.resources.model.BakedModel
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lain.engine.client.GameSession
import org.lain.engine.client.mc.ClientMixin
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.item.*
import org.lain.engine.client.resources.MeshModel
import org.lain.engine.client.resources.exportEngineModelTransformations
import org.lain.engine.util.math.Vec2
import kotlin.math.abs

private data class PendingTransformationRender(
    val itemStack: ItemStack,
    val renderInstance: TransformationRenderInstance,
    val context: EngineItemDisplayContext,
    val displayContext: ItemDisplayContext?,
    val leftHand: Boolean,
    val orthographic: Boolean,
    val projection: Matrix4f,
    val view: Matrix4f,
    val parent: Matrix4f,
)

internal data class TransformationRenderSnapshot(
    val context: EngineItemDisplayContext,
    val leftHand: Boolean,
    val orthographic: Boolean,
    val projection: Matrix4f,
    val view: Matrix4f,
    val parent: Matrix4f,
    val model: Matrix4f,
)

class TransformationEdit(
    private val gameSession: GameSession,
    val itemStack: ItemStack,
) {
    val modelId = ClientMixin.getEngineItemModelId(itemStack)
        ?: error("Transformation editor requires an Engine item model")

    var context = AdditionalTransformationsBank.lastDisplayContext
        ?.takeUnless { it == EngineItemDisplayContext.NONE }
        ?: EngineItemDisplayContext.FIRST_PERSON_RIGHT_HAND
        set(value) {
            field = value
            pendingRender = null
            renderSnapshot = null
            selectedRenderInstance = null
        }

    private var pendingRender: PendingTransformationRender? = null
    private var renderSnapshot: TransformationRenderSnapshot? = null
    private var selectedRenderInstance: TransformationRenderInstance? = null
    private val window = TransformationsEditorWindow(this)

    init {
        gameSession.client.imGuiManager.addWindow(window)
    }

    fun close() {
        pendingRender = null
        renderSnapshot = null
        selectedRenderInstance = null
        if (gameSession.transformationEdit === this) {
            gameSession.transformationEdit = null
        }
        gameSession.client.imGuiManager.close(window)
    }

    fun beginItemRender(
        renderedStack: ItemStack,
        displayContext: ItemDisplayContext,
        leftHand: Boolean,
        projection: Matrix4f,
        view: Matrix4f,
        parent: Matrix4f,
        renderInstance: TransformationRenderInstance,
    ) {
        pendingRender = null
        if (
            ClientMixin.getEngineItemModelId(renderedStack) != modelId ||
            context == EngineItemDisplayContext.NONE ||
            context == EngineItemDisplayContext.OUTFIT ||
            context.minecraft != displayContext
        ) {
            return
        }
        if (renderSnapshot != null || !selectRenderInstance(renderInstance)) return

        pendingRender = PendingTransformationRender(
            renderedStack,
            renderInstance,
            context,
            displayContext,
            leftHand,
            displayContext == ItemDisplayContext.GUI,
            Matrix4f(projection),
            Matrix4f(view),
            Matrix4f(parent),
        )
    }

    fun endItemRender(
        renderedStack: ItemStack,
        displayContext: ItemDisplayContext,
        leftHand: Boolean,
        model: Matrix4f,
        renderInstance: TransformationRenderInstance,
    ) {
        finishRender(renderedStack, renderInstance, context, displayContext, leftHand, model)
    }

    fun beginOutfitRender(
        renderedStack: ItemStack,
        projection: Matrix4f,
        view: Matrix4f,
        parent: Matrix4f,
        renderInstance: TransformationRenderInstance,
    ) {
        pendingRender = null
        if (
            ClientMixin.getEngineItemModelId(renderedStack) != modelId ||
            context != EngineItemDisplayContext.OUTFIT
        ) {
            return
        }
        if (renderSnapshot != null || !selectRenderInstance(renderInstance)) return

        pendingRender = PendingTransformationRender(
            renderedStack,
            renderInstance,
            context,
            null,
            false,
            false,
            Matrix4f(projection),
            Matrix4f(view),
            Matrix4f(parent),
        )
    }

    fun endOutfitRender(
        renderedStack: ItemStack,
        model: Matrix4f,
        renderInstance: TransformationRenderInstance,
    ) {
        finishRender(
            renderedStack,
            renderInstance,
            EngineItemDisplayContext.OUTFIT,
            null,
            false,
            model,
        )
    }

    internal fun consumeRenderSnapshot(): TransformationRenderSnapshot? {
        val snapshot = renderSnapshot
        renderSnapshot = null
        val matchingSnapshot = snapshot?.takeIf { it.context == context }
        if (matchingSnapshot == null) {
            selectedRenderInstance = null
        }
        return matchingSnapshot
    }

    private fun selectRenderInstance(renderInstance: TransformationRenderInstance): Boolean {
        val selected = selectedRenderInstance
        if (selected != null) return selected == renderInstance
        selectedRenderInstance = renderInstance
        return true
    }

    private fun finishRender(
        renderedStack: ItemStack,
        renderInstance: TransformationRenderInstance,
        capturedContext: EngineItemDisplayContext,
        displayContext: ItemDisplayContext?,
        leftHand: Boolean,
        model: Matrix4f,
    ) {
        val pending = pendingRender ?: return
        pendingRender = null
        if (
            pending.itemStack != renderedStack ||
            pending.renderInstance != renderInstance ||
            pending.context != capturedContext ||
            pending.context != context ||
            pending.displayContext != displayContext ||
            pending.leftHand != leftHand
        ) {
            return
        }

        renderSnapshot = TransformationRenderSnapshot(
            pending.context,
            pending.leftHand,
            pending.orthographic,
            pending.projection,
            pending.view,
            pending.parent,
            Matrix4f(model),
        )
    }
}

class TransformationsEditorWindow(
    private val edit: TransformationEdit
) : ImGuiWindow {
    private val modelId = edit.modelId

    override val title: String
        get() = "Редактор трансформаций ${modelId.path}"
    override val size: Vec2
        get() = Vec2(1650f, 800f)

    private val model = MinecraftClient.modelManager.getModel(modelId)
    private var transformations = AdditionalTransformationsBank.get(modelId) ?: computeTransformations(model)

    private fun computeTransformations(model: BakedModel): EngineTransformationsBundle {
        val engineModel = model as? MeshModel
        val transforms = model.transforms
        return engineModel?.properties?.transform ?: transforms.engine(EngineTransformation.Identity())
    }

    private var context: EngineItemDisplayContext
        get() = edit.context
        set(value) {
            edit.context = value
            AdditionalTransformationsBank.lastDisplayContext = value
        }

    private var gizmoOperation = Operation.TRANSLATE
    private var gizmoMode = Mode.WORLD
    private var hideGizmo = false

    init {
        AdditionalTransformationsBank.set(modelId, transformations)
    }

    override fun render() = with(transformations.getTransformation(context)) {
        val contextPanelWidth = minOf(CONTEXT_PANEL_WIDTH, ImGui.getContentRegionAvailX() * 0.4f)
        val controlsWidth = -(contextPanelWidth + ImGui.getStyle().itemSpacingX)

        if (ImGui.beginChild("Controls", controlsWidth, 0f)) {
            if (ImGui.button("Reset")) {
                transformations = computeTransformations(model)
                AdditionalTransformationsBank.set(modelId, transformations)
            }
            ImGui.sameLine()

            if (model is MeshModel) {
                if (ImGui.button("Export")) {
                    exportEngineModelTransformations(model, transformations)
                    AdditionalTransformationsBank.remove(modelId)
                }
            }
            ImGui.sameLine()

            if (ImGui.button("Copy partial")) {
                AdditionalTransformationsBank.clipboardSingleTransform =
                    EngineTransformation(translation, rotation, scale)
            }
            ImGui.sameLine()

            if (ImGui.button("Copy full")) {
                AdditionalTransformationsBank.clipboardFullTransform = transformations.copy()
            }
            ImGui.sameLine()

            if (ImGui.button("Paste partial")) {
                AdditionalTransformationsBank.clipboardSingleTransform?.let {
                    translation.set(it.translation)
                    rotation.set(it.rotation)
                    scale.set(it.scale)
                }
            }

            ImGui.sameLine()
            if (ImGui.button("Paste full")) {
                AdditionalTransformationsBank.clipboardFullTransform?.copy()?.let {
                    transformations = it
                    AdditionalTransformationsBank.set(modelId, transformations)
                }
            }

            ImGui.separator()
            renderGizmoControls()
            ImGui.separator()

            ImGui.text("Translation")
            renderSlider("translation", "X", 0f, MAX_TRANSLATION, { it.translation.x }, { translation.x = it })
            renderSlider("translation", "Y", 0f, MAX_TRANSLATION,{ it.translation.y }, { translation.y = it })
            renderSlider("translation", "Z", 0f, MAX_TRANSLATION,{ it.translation.z }, { translation.z = it })
            ImGui.separator()

            ImGui.text("Rotation")
            renderSlider("rotation", "X", 0f, MAX_ROTATION, { it.rotation.x }, { rotation.x = it })
            renderSlider("rotation", "Y", 0f, MAX_ROTATION, { it.rotation.y }, { rotation.y = it })
            renderSlider("rotation", "Z", 0f, MAX_ROTATION, { it.rotation.z }, { rotation.z = it })
            ImGui.separator()

            ImGui.text("Scale")
            renderSlider("scale", "X", 1f, MAX_SCALE, { it.scale.x }, { scale.x = it })
            renderSlider("scale", "Y", 1f, MAX_SCALE, { it.scale.y }, { scale.y = it })
            renderSlider("scale", "Z", 1f, MAX_SCALE, { it.scale.z }, { scale.z = it })
            ImGui.separator()
        }
        ImGui.endChild()
        ImGui.sameLine()

        if (ImGui.beginChild("Contexts", 0f, 0f, true)) {
            ImGui.text("Context")
            ImGui.separator()
            EngineItemDisplayContext.entries.filterNot { it == EngineItemDisplayContext.NONE }.forEach { contextItem ->
                if (ImGui.selectable(contextItem.displayName, contextItem == context)) {
                    context = contextItem
                }
            }
        }
        ImGui.endChild()
    }

    override fun postRender() {
        val snapshot = edit.consumeRenderSnapshot() ?: return
        if (hideGizmo) return
        val view = snapshot.view.get(FloatArray(16))
        val projection = snapshot.projection.get(FloatArray(16))
        val model = snapshot.model.get(FloatArray(16))

        ImGuizmo.setDrawList(ImGui.getForegroundDrawList())
        ImGuizmo.setOrthographic(snapshot.orthographic)
        ImGuizmo.setRect(
            0f, 0f,
            ImGui.getIO().displaySizeX,
            ImGui.getIO().displaySizeY
        )
        ImGuizmo.manipulate(
            view,
            projection,
            gizmoOperation,
            gizmoMode,
            model,
        )

        if (ImGuizmo.isUsing()) {
            applyGizmoTransformation(snapshot, model)
        }
    }

    private fun renderGizmoControls() {
        ImGui.text("Gizmo")
        if (ImGui.radioButton("Translate", gizmoOperation == Operation.TRANSLATE)) {
            gizmoOperation = Operation.TRANSLATE
        }
        ImGui.sameLine()
        if (ImGui.radioButton("Rotate", gizmoOperation == Operation.ROTATE)) {
            gizmoOperation = Operation.ROTATE
        }
        ImGui.sameLine()
        if (ImGui.radioButton("Scale", gizmoOperation == Operation.SCALE)) {
            gizmoOperation = Operation.SCALE
        }

        if (ImGui.radioButton("Local", gizmoMode == Mode.LOCAL)) {
            gizmoMode = Mode.LOCAL
        }
        ImGui.sameLine()
        if (ImGui.radioButton("World", gizmoMode == Mode.WORLD)) {
            gizmoMode = Mode.WORLD
        }
        ImGui.sameLine()
        if (ImGui.radioButton("Hide", hideGizmo)) {
            hideGizmo = !hideGizmo
        }
    }

    private fun applyGizmoTransformation(snapshot: TransformationRenderSnapshot, model: FloatArray) {
        val inverseParent = Matrix4f(snapshot.parent)
        val determinant = inverseParent.determinant()
        if (!determinant.isFinite() || abs(determinant) < MIN_INVERTIBLE_DETERMINANT) {
            return
        }

        val local = inverseParent.invert().mul(Matrix4f().set(model))
        val transformation = transformations.getTransformation(snapshot.context)
        when (gizmoOperation) {
            Operation.TRANSLATE -> {
                val translated = local.getTranslation(Vector3f())
                if (!translated.isFinite) return
                transformation.translation.set(
                    if (snapshot.leftHand) -translated.x else translated.x,
                    translated.y,
                    translated.z,
                )
            }

            Operation.ROTATE -> {
                val rotated = local
                    .getUnnormalizedRotation(Quaternionf())
                    .normalize()
                    .getEulerAnglesXYZ(Vector3f())
                    .mul(RADIANS_TO_DEGREES)
                if (!rotated.isFinite) return

                val rotationX = rotated.x
                val rotationY = if (snapshot.leftHand) -rotated.y else rotated.y
                val rotationZ = if (snapshot.leftHand) -rotated.z else rotated.z
                transformation.rotation.set(
                    nearestEquivalentAngle(rotationX, transformation.rotation.x),
                    nearestEquivalentAngle(rotationY, transformation.rotation.y),
                    nearestEquivalentAngle(rotationZ, transformation.rotation.z),
                )
            }

            Operation.SCALE -> {
                val scaled = local.getScale(Vector3f())
                if (!scaled.isFinite) return
                transformation.scale.set(scaled.x, scaled.y, scaled.z)
            }
        }
        AdditionalTransformationsBank.set(modelId, transformations)
    }

    private fun nearestEquivalentAngle(value: Float, reference: Float): Float {
        val delta = ((value - reference + HALF_ROTATION) % FULL_ROTATION + FULL_ROTATION) % FULL_ROTATION - HALF_ROTATION
        return reference + delta
    }

    context(transformation: EngineTransformation)
    fun renderSlider(
        category: String,
        option: String,
        default: Float,
        maxValue: Float,
        getter: (EngineTransformation) -> Float,
        setter: (Float) -> Unit
    ) {
        val value = getter(transformation)
        val valueArr = floatArrayOf(value)
        ImGui.text(option)
        ImGui.sameLine()
        renderSliderTools(category, option, default, getter, setter)
        ImGui.sameLine()
        if (ImGui.sliderFloat("##$option-$category", valueArr, -maxValue, maxValue)) {
            setter(valueArr[0])
        }
        ImGui.sameLine()
        val valueFloat = ImFloat(value)
        if (ImGui.inputFloat("##$option-$category-input", valueFloat, 0.1f)) {
            val newValue = valueFloat.get()
            setter(newValue)
        }
    }

    fun renderSliderTools(category: String, option: String, default: Float, getter: (EngineTransformation) -> Float, setter: (Float) -> Unit) {
        if (ImGui.button("Reset##$category-$option")) {
            setter(getter(computeTransformations(model).getTransformation(context)))
        }
        ImGui.sameLine()
        if (ImGui.button("Null##$category-$option")) {
            setter(default)
        }
    }

    override fun onClose() {
        if (computeTransformations(model) == transformations) {
            AdditionalTransformationsBank.remove(modelId)
        }
        edit.close()
    }

    companion object {
        private const val CONTEXT_PANEL_WIDTH = 380f
        private const val MAX_TRANSLATION = 3f
        private const val MAX_SCALE = 3f
        private const val MAX_ROTATION = 180f
        private const val MIN_INVERTIBLE_DETERMINANT = 1e-8f
        private const val RADIANS_TO_DEGREES = 57.29578f
        private const val HALF_ROTATION = 180f
        private const val FULL_ROTATION = 360f
    }
}
