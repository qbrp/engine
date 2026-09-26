package org.lain.engine.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.lain.engine.mc.PlayerEntityAccess;
import org.lain.engine.mc.PlayerEntityAccessHolder;
import org.lain.engine.mc.ServerMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Unique
    @Nullable
    private PlayerEntityAccess engine$getPlayerEntityAccess() {
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
        PlayerEntityAccess access = engine$getPlayerEntityAccess();
        if (access != null) {
            if (is(holder, Attributes.MOVEMENT_SPEED)) {
                cir.setReturnValue((double)access.getSpeed());
            } else if (is(holder, Attributes.JUMP_STRENGTH)) {
                cir.setReturnValue((double)access.getJumpStrength());
            }
        }
    }

    @Redirect(
            method = "setSprinting",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/attributes/AttributeInstance;addTransientModifier(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V"
            )
    )
    private void engine$cancelTransientModifierAdd(AttributeInstance instance, AttributeModifier attributeModifier) {}

    @Inject(
            method = "getScale",
            at = @At("HEAD"),
            cancellable = true
    )
    public void engine$getScale(CallbackInfoReturnable<Float> cir) {
        PlayerEntityAccess access = engine$getPlayerEntityAccess();
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
            method = "hurt",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onDamage(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.level() instanceof ServerLevel && ServerMixin.INSTANCE.shouldCancelDamage()) {
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
        PlayerEntityAccess access = engine$getPlayerEntityAccess();
        if (access != null) {
            if (access.canJump()) {
                access.onJump();
            } else {
                ci.cancel();
            }
        }
    }
}
