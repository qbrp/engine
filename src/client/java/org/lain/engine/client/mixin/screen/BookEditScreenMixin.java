package org.lain.engine.client.mixin.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lain.engine.client.mc.ClientMixin;
import org.lain.engine.client.mixin.render.ScreenAccessor;
import org.lain.engine.item.Writable;
import org.lain.engine.mc.CommonUtilKt;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Unique
    private Writable engine$writable;

    @Unique
    private ResourceLocation engine$backgroundTexture;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void engine$initialize(Player player, ItemStack stack, InteractionHand hand, CallbackInfo ci) {
        this.engine$writable = ClientMixin.INSTANCE.getWriteable(stack);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/BookEditScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;"
            )
    )
    private GuiEventListener engine$removeSignButton(BookEditScreen screen, GuiEventListener widget) {
        if (this.engine$writable != null
                && widget instanceof Button button
                && button.getMessage().getContents() instanceof TranslatableContents contents
                && Objects.equals(contents.getKey(), "book.signButton")) {
            return widget;
        }

        ((ScreenAccessor)(Object)this).engine$addDrawableChild(widget);
        return widget;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void engine$resolveBackground(CallbackInfo ci) {
        if (this.engine$writable != null && this.engine$writable.getBackgroundAsset() != null) {
            this.engine$backgroundTexture = CommonUtilKt.engineId(this.engine$writable.getBackgroundAsset());
        }
    }

    @Redirect(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
            )
    )
    private void engine$renderBackground(
            GuiGraphics guiGraphics,
            ResourceLocation vanillaTexture,
            int x,
            int y,
            int u,
            int v,
            int width,
            int height
    ) {
        guiGraphics.blit(
                this.engine$backgroundTexture != null ? this.engine$backgroundTexture : vanillaTexture,
                x,
                y,
                u,
                v,
                width,
                height
        );
    }

    @ModifyConstant(method = "appendPageToBook", constant = @Constant(intValue = 100))
    private int engine$clampPages(int vanillaLimit) {
        return this.engine$writable != null ? this.engine$writable.getPages() : vanillaLimit;
    }

    @Inject(method = "saveChanges", at = @At("HEAD"), cancellable = true)
    private void engine$saveWritable(boolean signing, CallbackInfo ci) {
        if (this.engine$writable == null) {
            return;
        }

        while (!this.pages.isEmpty() && this.pages.get(this.pages.size() - 1).isEmpty()) {
            this.pages.remove(this.pages.size() - 1);
        }
        ClientMixin.INSTANCE.onBookClose(this.book, this.engine$writable, List.copyOf(this.pages));
        ci.cancel();
    }
}
