package org.lain.engine.mixin;

import kotlinx.datetime.Ser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import org.lain.engine.mc.ServerMixinAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @Inject(
            method = "getAttributeValue",
            at = @At(value = "RETURN"),
            cancellable = true
    )
    public void engine$getAttributeValue(RegistryEntry<EntityAttribute> attribute, CallbackInfoReturnable<Double> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            ServerMixinAccess engine = ServerMixinAccess.INSTANCE;

            if (is(attribute, EntityAttributes.MOVEMENT_SPEED)) {
                cir.setReturnValue(engine.getSpeed(player));
            } else if (is(attribute, EntityAttributes.JUMP_STRENGTH)) {
                cir.setReturnValue(engine.getJumpStrength(player));
            }
        }
    }

    @Unique
    private boolean is(RegistryEntry<EntityAttribute> entry, RegistryEntry<EntityAttribute> entry2) {
        Optional<RegistryKey<EntityAttribute>> key = entry2.getKey();
        return key.map(entityAttributeRegistryKey -> entry.getKey().map(k -> k == entityAttributeRegistryKey).orElse(false)).orElse(false);
    }

    @Inject(
            method = "damage",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (ServerMixinAccess.INSTANCE.shouldCancelDamage()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
