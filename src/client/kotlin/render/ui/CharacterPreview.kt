package org.lain.engine.client.render.ui

import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.ClientAsset
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.ItemStack

fun createCharacterPreviewRenderState(
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

        pose = Pose.STANDING
        mainArm = HumanoidArm.RIGHT
        attackArm = HumanoidArm.RIGHT
        useItemHand = InteractionHand.MAIN_HAND
        rightArmPose = HumanoidModel.ArmPose.EMPTY
        leftArmPose = HumanoidModel.ArmPose.EMPTY

        speedValue = 1.0f
        showHat = true
        showJacket = true
        showLeftPants = true
        showRightPants = true
        showLeftSleeve = true
        showRightSleeve = true
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
