package org.lain.engine.client.mixin.resource;

import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.resources.ItemAssetLoaderKt;
import org.lain.engine.client.resources.ModelLoaderKt;
import org.lain.engine.client.resources.ResourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(ModelManager.class)
public class BakedModelManagerMixin {
    @Unique
    private static ResourceList resources() {
        return ClientMixinAccess.INSTANCE.getResourceList();
    }

    @Inject(
            method = "loadBlockModels",
            at = @At(
                    value = "RETURN"
            ),
            cancellable = true)
    private static void engine$reloadModels(ResourceManager resourceManager, Executor executor, CallbackInfoReturnable<CompletableFuture<Map<Identifier, UnbakedModel>>> cir) {
        CompletableFuture<Map<Identifier, UnbakedModel>> returnValue = cir.getReturnValue();
        cir.setReturnValue(
            returnValue.thenApply((res) -> {
                Map<Identifier, UnbakedModel> models = new HashMap<>();
                models.putAll(ModelLoaderKt.autogenerateModels(resources().getGeneratedItemAssets()));
                models.putAll(ModelLoaderKt.parseEngineItemModels(resources().getItemModels(), resources().getObjModels()));
                models.putAll(res);
                return models;
            })
        );
    }

    @Redirect(
            method = "reload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/ClientItemInfoLoader;scheduleLoad(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private static CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> engine$loadItemAssets(ResourceManager resourceManager, Executor executor) {
        return ClientItemInfoLoader.scheduleLoad(resourceManager, executor).thenApply((r) -> {
            Map<Identifier, ClientItem> contents = r.contents();
            contents.putAll(ItemAssetLoaderKt.parseEngineItemAssets(resources().getAllItemAssets()));
            new ClientItemInfoLoader.LoadedClientInfos(contents);
            return r;
        });
    }
}
