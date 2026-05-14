package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.block.model.ItemTransform
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.resources.model.ResolvableModel
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Vector3fc
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.render.item.EngineItemDisplayContext
import org.lain.engine.client.render.item.culling
import org.lain.engine.client.render.item.engineOutfit
import org.lain.engine.client.render.item.engineTransformation
import org.lain.engine.mc.ITEM_STACK_MATERIAL

class EngineItemModel(
    val asset: Asset,
    val itemModel: ItemModel,
    val disableCulling: Boolean,
    val markers: Map<String, Vector3fc>,
    val outfitTransformation: ItemTransform?
) : ItemModel by itemModel {
    override fun update(
        state: ItemStackRenderState,
        stack: ItemStack,
        resolver: ItemModelResolver,
        displayContext: ItemDisplayContext,
        world: ClientLevel?,
        heldItemContext: ItemOwner?,
        seed: Int
    ) {
        itemModel.update(state, stack, resolver, displayContext, world, heldItemContext, seed)
        state.culling = !disableCulling
        if (state.engineOutfit != null && outfitTransformation != null) {
            state.engineTransformation = outfitTransformation
        }
    }

    fun updateEngine(
        state: ItemStackRenderState,
        stack: ItemStack?,
        displayContext: EngineItemDisplayContext,
        world: ClientLevel?,
        heldItemContext: ItemOwner?,
        seed: Int
    ) {
        itemModel.update(
            state,
            stack ?: ITEM_STACK_MATERIAL,
            MinecraftClient.itemModelResolver,
            displayContext.minecraft,
            world,
            heldItemContext,
            seed
        )
        state.culling = !disableCulling
        if (displayContext == EngineItemDisplayContext.OUTFIT) {
            state.engineTransformation = outfitTransformation
        }
    }

    class Unbaked(
        val asset: Asset,
        val model: ItemModel.Unbaked,
        val disableCulling: Boolean,
        val markers: Map<String, Vector3fc>,
        val outfitTransformation: ItemTransform?
    ) : ItemModel.Unbaked {
        override fun type(): MapCodec<out ItemModel.Unbaked> { throw AssertionError() }

        override fun bake(bakingContext: ItemModel.BakingContext): ItemModel {
            return EngineItemModel(
                asset,
                model.bake(bakingContext),
                disableCulling,
                markers,
                outfitTransformation
            )
        }

        override fun resolveDependencies(resolver: ResolvableModel.Resolver) {
            model.resolveDependencies(resolver)
        }
    }
}