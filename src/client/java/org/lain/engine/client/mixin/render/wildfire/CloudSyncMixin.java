package org.lain.engine.client.mixin.render.wildfire;

import com.wildfire.main.cloud.CloudSync;
import com.wildfire.main.cloud.SyncUnavailable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CloudSync.class)
public class CloudSyncMixin {
    @Inject(
            method = "unavailableReason",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void engine$disableCloudSync(CallbackInfoReturnable<SyncUnavailable> cir) {
        cir.setReturnValue(
                SyncUnavailable.OFFLINE_SERVER
        );
    }
}
