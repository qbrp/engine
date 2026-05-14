package org.lain.engine.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(
            method = "getAttributeValue",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getAttributeValue(Holder<Attribute> holder, CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof Player player) {
            ServerMixinAccess engine = ServerMixinAccess.INSTANCE;

            if (is(holder, Attributes.MOVEMENT_SPEED)) {
                cir.setReturnValue(engine.getSpeed(player));
            } else if (is(holder, Attributes.JUMP_STRENGTH)) {
                cir.setReturnValue(engine.getJumpStrength(player));
            }
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
        if (ServerMixinAccess.INSTANCE.shouldCancelDamage()) {
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
        if ((Object) this instanceof Player player) {
            if (ServerMixinAccess.INSTANCE.canJump(player)) {
                ServerMixinAccess.INSTANCE.onPlayerJump(player);
            } else {
                ci.cancel();
            }
        }
    }
}
