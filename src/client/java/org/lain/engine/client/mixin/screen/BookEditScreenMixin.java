package org.lain.engine.client.mixin.screen;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.type.WritableBookContentComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lain.engine.client.mc.ClientMixinAccess;
import org.lain.engine.client.mixin.render.ScreenAccessor;
import org.lain.engine.item.Writable;
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
public class BookEditScreenMixin {
    @Shadow
    @Final
    private ItemStack stack;
    @Shadow
    @Final
    private List<String> pages;
    @Unique
    private Writable writable;
    @Unique
    private Identifier backgroundTextureId;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    public void engine$initialize(PlayerEntity player, ItemStack stack, Hand hand, WritableBookContentComponent writableBookContent, CallbackInfo ci) {
        writable = ClientMixinAccess.INSTANCE.getWriteable(stack);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/ingame/BookEditScreen;addDrawableChild(Lnet/minecraft/client/gui/Element;)Lnet/minecraft/client/gui/Element;"
            )
    )
    public Element engine$removeDoneButton(BookEditScreen instance, Element element) {
        if (writable != null) {
            if (element instanceof ButtonWidget && ((ButtonWidget)element).getMessage().getContent() instanceof TranslatableTextContent) {
                TranslatableTextContent content = (TranslatableTextContent)((ButtonWidget)element).getMessage().getContent();
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
            backgroundTextureId = IdKt.EngineId(writable.getBackgroundAsset());
        }
    }

    @Redirect(
            method = "renderBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"
            )
    )
    public void engine$redirectRenderBackground(DrawContext instance, RenderPipeline pipeline, Identifier sprite, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        if (backgroundTextureId != null) {
            instance.drawGuiTexture(pipeline, backgroundTextureId, textureWidth, textureHeight, Math.round(u * textureWidth), Math.round(v * textureHeight), x, y, width, height);
        } else {
            instance.drawTexture(pipeline, sprite, x, y, u, v, width, height, textureWidth, textureHeight);
        }
    }

    @ModifyConstant(
            method = "appendNewPage",
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
            method = "finalizeBook",
            at = @At(
                    value = "RETURN",
                    target = "Lnet/minecraft/client/gui/screen/ingame/BookEditScreen;writeNbtData()V"
            ),
            cancellable = true
    )
    public void engine$finalize(CallbackInfo ci) {
        if (writable != null) {
            ClientMixinAccess.INSTANCE.onBookClose(stack, writable, pages);
            ci.cancel();
        }
    }
}
