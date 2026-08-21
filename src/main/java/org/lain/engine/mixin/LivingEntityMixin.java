package org.lain.engine.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.lain.engine.mc.CommonMixin;
import org.lain.engine.mc.PlayerEntityAccessHolder;
import org.lain.engine.mc.ServerMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Unique
    @Nullable
    private CommonMixin.PlayerEntityAccess engine$getPlayerEntityAccess() {
        if ((Object) this instanceof PlayerEntityAccessHolder holder) {
            return holder.engine$getPlayerEntityAccess();
        }
        return null;
    }

    @Inject(
            method = "getAttributeValue",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getAttributeValue(Holder<Attribute> holder, CallbackInfoReturnable<Double> cir) {
        CommonMixin.PlayerEntityAccess access = engine$getPlayerEntityAccess();
        if (access != null) {
            if (is(holder, Attributes.MOVEMENT_SPEED)) {
                cir.setReturnValue(access.getSpeed());
            } else if (is(holder, Attributes.JUMP_STRENGTH)) {
                cir.setReturnValue(access.getJumpStrength());
            }
        }
    }

    @Inject(
            method = "getScale",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$getScale(CallbackInfoReturnable<Float> cir) {
        CommonMixin.PlayerEntityAccess access = engine$getPlayerEntityAccess();
        if (access != null) {
            cir.setReturnValue(access.getScale());
        }
    }

    @Unique
    private boolean is(Holder<Attribute> entry, Holder<Attribute> entry2) {
        Optional<ResourceKey<Attribute>> key = entry2.unwrapKey();
        return key.map(
                entityAttributeRegistryKey ->entry.unwrapKey()
                        .map(k -> k == entityAttributeRegistryKey).orElse(false)).
                orElse(false
        );
    }

    @Inject(
            method = "hurtServer",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onDamage(ServerLevel serverLevel, DamageSource damageSource, float f, CallbackInfoReturnable<Boolean> cir) {
        if (ServerMixin.INSTANCE.shouldCancelDamage()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(
            method = "jumpFromGround",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$jump(CallbackInfo ci) {
        CommonMixin.PlayerEntityAccess access = engine$getPlayerEntityAccess();
        if (access != null) {
            if (access.canJump()) {
                access.onPlayerJump();
            } else {
                ci.cancel();
            }
        }
    }
}
