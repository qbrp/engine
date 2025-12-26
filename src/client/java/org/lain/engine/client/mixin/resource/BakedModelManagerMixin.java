package org.lain.engine.client.mixin.resource;

import net.minecraft.client.item.ItemAsset;
import net.minecraft.client.item.ItemAssetsLoader;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.UnbakedModel;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
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

@Mixin(BakedModelManager.class)
public class BakedModelManagerMixin {
    @Unique
    private static ResourceList resources;

    @Inject(
            method = "reload",
            at = @At(value = "HEAD")
    )
    private static void engine$reload(ResourceReloader.Synchronizer synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        resources = ClientMixinAccess.INSTANCE.createResourceList();
    }

    @Inject(
            method = "reloadModels",
            at = @At(
                    value = "RETURN"
            ),
            cancellable = true)
    private static void engine$reloadModels(ResourceManager resourceManager, Executor executor, CallbackInfoReturnable<CompletableFuture<Map<Identifier, UnbakedModel>>> cir) {
        CompletableFuture<Map<Identifier, UnbakedModel>> returnValue = cir.getReturnValue();
        cir.setReturnValue(
            returnValue.thenApply((res) -> {
                Map<Identifier, UnbakedModel> models = new HashMap<>();
                models.putAll(ModelLoaderKt.autogenerateModels(resources.getGeneratedItemAssets()));
                models.putAll(ModelLoaderKt.parseEngineModels(resources.getItemModels(), resources.getObjModels()));
                models.putAll(res);
                return models;
            })
        );
    }

    @Redirect(
            method = "reload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/item/ItemAssetsLoader;load(Lnet/minecraft/resource/ResourceManager;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"
            )
    )
    private static CompletableFuture<ItemAssetsLoader.Result> engine$loadItemAssets(ResourceManager resourceManager, Executor executor) {
        return ItemAssetsLoader.load(resourceManager, executor).thenApply((r) -> {
            Map<Identifier, ItemAsset> contents = r.contents();
            contents.putAll(ItemAssetLoaderKt.parseEngineItemAssets(resources.getAllItemAssets()));
            new ItemAssetsLoader.Result(contents);
            return r;
        });
    }
}
