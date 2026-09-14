package org.lain.engine.client.mixin.render;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.client.render.player.EnginePlayerRenderState;
import org.lain.engine.client.render.player.PlayerRenderStateKt;
import org.lain.engine.player.ArmPose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart head;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/world/entity/LivingEntity;F)V"
            )
    )
    private void engine$setAngles(
            LivingEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        if (!(entity instanceof Player player)) {
            return;
        }

        EnginePlayerRenderState renderState = PlayerRenderStateKt.getEngineRenderState(player);
        if (renderState == null) {
            return;
        }

        ArmPose mainArmPose = renderState.getMainArmPose();
        ArmPose minorArmPose = renderState.getMinorArmPose();
        if (mainArmPose == ArmPose.EXPOSE) {
            holdSingle(this.rightArm, this.head, true);
        } else if (mainArmPose == ArmPose.HOLD_WEAPON) {
            AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, true);
        }

        if (minorArmPose == ArmPose.EXPOSE) {
            holdSingle(this.leftArm, this.head, false);
        } else if (minorArmPose == ArmPose.HOLD_WEAPON) {
            AnimationUtils.animateCrossbowHold(this.rightArm, this.leftArm, this.head, false);
        }
    }

    @Unique
    private static void holdSingle(ModelPart arm, ModelPart head, boolean isRight) {
        float spread = 0.05f;
        arm.yRot = head.yRot + (isRight ? -spread : spread);
        arm.xRot = -1.5707964f + head.xRot + 0.1f;
    }
}
