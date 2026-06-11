package org.lain.engine.client.render.item

import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.block.model.ItemTransforms
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import org.lain.engine.mc.engine
import org.lain.engine.mc.minecraft
import org.lain.engine.util.math.MutableEVec3

object AdditionalTransformationsBank {
    private val modelToTransformations: MutableMap<Identifier, EngineTransformationsBundle> = mutableMapOf()
    var clipboardSingleTransform: EngineTransformation? = null
    var clipboardFullTransform: EngineTransformationsBundle? = null
    var lastDisplayContext: EngineItemDisplayContext? = null

    fun set(model: Identifier, transformations: EngineTransformationsBundle) {
        modelToTransformations[model] = transformations.copy()
    }

    fun remove(model: Identifier) = modelToTransformations.remove(model)

    fun get(model: Identifier) = modelToTransformations[model]?.copy()
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

data class EngineTransformationsBundle(
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
    val translation: MutableEVec3,
    val rotation: MutableEVec3,
    val scale: MutableEVec3
) {
    val isIdentity get() = translation.isZero() && rotation.isZero() && scale.isZero()

    companion object {
        fun Identity() = EngineTransformation(
            MutableEVec3(0f, 0f, 0f),
            MutableEVec3(0f, 0f, 0f),
            MutableEVec3(1f, 1f, 1f)
        )
    }
}

// ADAPTERS

fun ItemTransform.engine(): EngineTransformation {
    return EngineTransformation(translation.engine(), rotation.engine(), scale.engine())
}

fun EngineTransformation.minecraft(): ItemTransform {
    return ItemTransform(rotation.minecraft(), translation.minecraft(), scale.minecraft())
}

fun EngineTransformationsBundle.minecraft() = ItemTransforms(
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
