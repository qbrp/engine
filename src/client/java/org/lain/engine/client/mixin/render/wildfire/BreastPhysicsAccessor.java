package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.api.IGenderArmor;
import com.wildfire.main.config.enums.Gender;
import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.physics.BreastPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BreastPhysics.class, remap = false)
public interface BreastPhysicsAccessor {
    @Invoker("simplifiedTick")
    void engine$simplifiedTick(IGenderArmor armor);
}