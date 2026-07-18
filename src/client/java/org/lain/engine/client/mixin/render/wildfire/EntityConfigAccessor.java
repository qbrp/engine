package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.physics.BreastPhysics;
import com.wildfire.render.GenderRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = EntityConfig.class, remap = false)
public interface EntityConfigAccessor {
    @Mutable
    @Accessor("gender")
    void engine$setGender(Gender gender);

    @Mutable
    @Accessor("pBustSize")
    void engine$setPBustSize(float bustSize);

    @Mutable
    @Accessor("lBreastPhysics")
    void engine$setLBreastPhysics(BreastPhysics physics);

    @Mutable
    @Accessor("rBreastPhysics")
    void engine$setRBreastPhysics(BreastPhysics physics);
}