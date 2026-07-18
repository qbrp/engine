package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.main.entitydata.EntityConfig;
import com.wildfire.render.GenderRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GenderRenderState.class, remap = false)
public interface GenderRenderStateAccessor {
    @Accessor("STATE")
    static RenderStateDataKey<GenderRenderState> engine$getRenderStateDataKey() {
        throw new AssertionError();
    }

    @Invoker("<init>")
    static GenderRenderState engine$create(EntityConfig config, LivingEntity entity) {
        throw new AssertionError();
    }
}
