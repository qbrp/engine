package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
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
import org.lain.engine.client.mc.render.EngineItemDisplayContext
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.player.Outfit

private val CULLING = RenderStateDataKey.create<Boolean> { "Engine culling" }

var ItemStackRenderState.culling: Boolean?
    get() = this.getData(CULLING)
    set(value) { setData(CULLING, value) }

private val ENGINE_TRANSFORMATION = RenderStateDataKey.create<ItemTransform> { "Engine transformation" }

var ItemStackRenderState.engineTransformation: ItemTransform?
    get() = this.getData(ENGINE_TRANSFORMATION)
    set(value) { setData(ENGINE_TRANSFORMATION, value) }

private val ENGINE_OUTFIT = RenderStateDataKey.create<Outfit> { "Engine outfit" }

var ItemStackRenderState.engineOutfit: Outfit?
    get() = this.getData(ENGINE_OUTFIT)
    set(value) { setData(ENGINE_OUTFIT, value) }

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