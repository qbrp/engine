package org.lain.engine.client.mixin.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mixin.render.ScreenAccessor;
import org.lain.engine.item.Writable;
import org.lain.engine.mc.CommonUtilKt;
import org.lain.engine.util.IdKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Objects;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {
    @Shadow
    @Final
    private ItemStack book;
    @Shadow
    @Final
    private List<String> pages;

    @Shadow
    protected abstract void visitText(ActiveTextCollector activeTextCollector);

    @Unique
    private Writable writable;
    @Unique
    private Identifier backgroundTextureId;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void engine$initialize(Player player, ItemStack stack, InteractionHand hand, WritableBookContent writableBookContent, CallbackInfo ci) {
        writable = ClientMixinAccess.INSTANCE.getWriteable(stack);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/BookEditScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    public GuiEventListener engine$removeDoneButton(BookEditScreen instance, GuiEventListener element) {
        if (writable != null) {
            if (element instanceof Button && ((Button)element).getMessage().getContents() instanceof TranslatableContents) {
                TranslatableContents content = (TranslatableContents)((Button)element).getMessage().getContents();
                if (Objects.equals(content.getKey(), "book.signButton")) {
                    return element;
                }
            }
        }
        ((ScreenAccessor)(Object)this).engine$addDrawableChild(element);
        return element;
    }

    @Inject(
            method = "init",
            at = @At("TAIL")
    )
    public void engine$init(CallbackInfo ci) {
        if (writable != null && writable.getBackgroundAsset() != null) {
            backgroundTextureId = CommonUtilKt.engineId(writable.getBackgroundAsset());
        }
    }

    @Redirect(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"
            )
    )
    public void engine$redirectRenderBackground(GuiGraphics instance, RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        if (backgroundTextureId != null) {
            instance.blitSprite(pipeline, backgroundTextureId, textureWidth, textureHeight, Math.round(u * textureWidth), Math.round(v * textureHeight), x, y, width, height);
        } else {
            instance.blit(pipeline, sprite, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

//    @Inject(
//            method = "render",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    public void engine$redirectRenderBackground(GuiGraphics guiGraphics, int i, int j, float f, CallbackInfo ci) {
//        ci.cancel();
//        if (writable == null) {
//            visitText(guiGraphics.textRenderer());
//        }
//    }

    @ModifyConstant(
            method = "appendPageToBook",
            constant = @Constant(intValue = 100)
    )
    public int engine$clampPages(int constant) {
        if (writable != null) {
            return writable.getPages();
        } else {
            return constant;
        }
    }

    @Inject(
            method = "saveChanges",
            at = @At(
                    value = "RETURN",
                    target = "Lnet/minecraft/client/gui/screen/ingame/BookEditScreen;writeNbtData()V"
            ),
            cancellable = true
    )
    public void engine$finalize(CallbackInfo ci) {
        if (writable != null) {
            ClientMixinAccess.INSTANCE.onBookClose(book, writable, pages);
            ci.cancel();
        }
    }
}
