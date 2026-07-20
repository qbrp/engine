package org.lain.engine.client.render.ui.character

import com.wildfire.api.IGenderArmor
import com.wildfire.main.WildfireGender
import com.wildfire.physics.BreastPhysics
import com.wildfire.render.GenderRenderState
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.ItemStack
import org.lain.engine.client.mixin.render.wildfire.BreastPhysicsAccessor
import org.lain.engine.client.mixin.render.wildfire.EntityConfigAccessor
import org.lain.engine.client.mixin.render.wildfire.GenderRenderStateAccessor
import org.lain.engine.mc.GENDER_MOD_AVAILABLE
import org.lain.engine.mc.wildfireGender
import org.lain.engine.player.character.BiologicalSex
import org.lain.engine.player.character.CharacterProfile
import java.util.UUID

fun createCharacterPreviewRenderState(
    character: CharacterProfile,
    skin: PlayerSkin,
    scale: Float,
): AvatarRenderState {
    return AvatarRenderState().apply {
        this.skin = skin
        this.scale = scale
        ageScale = scale
        boundingBoxWidth = 0.6f
        boundingBoxHeight = 1.8f
        eyeHeight = 1.62f

        if (GENDER_MOD_AVAILABLE) {
            setData(
                GenderRenderStateAccessor.`engine$getRenderStateDataKey`(),
                createGenderRenderState(
                    UUID.fromString(character.id),
                    character.genderParams.breastSize,
                    character.biologicalSex
                )
            )
        }

        pose = Pose.STANDING
        mainArm = HumanoidArm.RIGHT
        attackArm = HumanoidArm.RIGHT
        useItemHand = InteractionHand.MAIN_HAND
        rightArmPose = HumanoidModel.ArmPose.EMPTY
        leftArmPose = HumanoidModel.ArmPose.EMPTY

        speedValue = 1.0f
        showCape = false

        isSpectator = false
        isCrouching = false
        isFallFlying = false
        isVisuallySwimming = false
        isPassenger = false
        isUsingItem = false
        isBaby = false
        isInvisibleToPlayer = false

        headEquipment = ItemStack.EMPTY
        chestEquipment = ItemStack.EMPTY
        legsEquipment = ItemStack.EMPTY
        feetEquipment = ItemStack.EMPTY

        scoreText = null
        parrotOnLeftShoulder = null
        parrotOnRightShoulder = null
        bedOrientation = null
        wornHeadType = null
        wornHeadProfile = null
    }
}

fun createGenderRenderState(characterId: UUID, bustSize: Float, biologicalSex: BiologicalSex): GenderRenderState {
    val config = WildfireGender.getOrAddPlayerById(characterId)
    val accessor = config as EntityConfigAccessor
    accessor.`engine$setGender`(biologicalSex.wildfireGender)
    accessor.`engine$setPBustSize`(bustSize)
    accessor.`engine$setLBreastPhysics`(
        BreastPhysics(config).also { (it as BreastPhysicsAccessor).`engine$simplifiedTick`(IGenderArmor.EMPTY) }
    )
    accessor.`engine$setRBreastPhysics`(
        BreastPhysics(config).also { (it as BreastPhysicsAccessor).`engine$simplifiedTick`(IGenderArmor.EMPTY) }
    )
    val genderRenderState = GenderRenderStateAccessor.`engine$create`(
        config,
        null
    )
    return genderRenderState
}