package org.lain.engine.client.mixin.render;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.ArmPosing;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Arm;
import org.lain.engine.client.mc.render.PlayerRenderStateKt;
import org.lain.engine.player.ArmPose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {
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
            method = "setAngles(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/BipedEntityModel;animateArms(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;F)V"
            )
    )
    private void engine$setAngles(BipedEntityRenderState renderState, CallbackInfo ci) {
        if (renderState instanceof PlayerEntityRenderState state) {
            ArmPose mainArmPose = PlayerRenderStateKt.getMainArmPose(state);
            ArmPose minorArmPose = PlayerRenderStateKt.getMinorArmPose(state);

            if (mainArmPose == ArmPose.EXPOSE) {
                holdSingle(this.rightArm, this.head, true);
            } else if (mainArmPose == ArmPose.HOLD_WEAPON) {
                ArmPosing.hold(this.rightArm, this.leftArm, this.head, true);
            }

            if (minorArmPose == ArmPose.EXPOSE) {
                holdSingle(this.leftArm, this.head, false);
            } else if (minorArmPose == ArmPose.HOLD_WEAPON) {
                ArmPosing.hold(this.rightArm, this.leftArm, this.head, false);
            }
        }
    }

    @Unique
    private static void holdSingle(ModelPart arm, ModelPart head, boolean isRight) {
        float spread = 0.05f;
        arm.yaw = head.yaw + (isRight ? -spread : spread);
        arm.pitch = -1.5707964f + head.pitch + 0.1f;
    }
}
