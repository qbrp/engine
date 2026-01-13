package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.item.ItemModelManager
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.model.BasicItemModel
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.client.render.item.tint.TintSource
import net.minecraft.client.render.model.BakedQuad
import net.minecraft.client.render.model.ModelSettings
import net.minecraft.client.render.model.ResolvableModel
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.util.HeldItemContext

private val CULLING = RenderStateDataKey.create<Boolean> { "Engine culling" }

var ItemRenderState.culling: Boolean?
    get() = this.getData(CULLING)
    set(value) { setData(CULLING, value) }

class EngineItemModel(
    val itemModel: ItemModel,
    val disableCulling: Boolean
) : ItemModel by itemModel {
    override fun update(
        state: ItemRenderState,
        stack: ItemStack,
        resolver: ItemModelManager,
        displayContext: ItemDisplayContext,
        world: ClientWorld?,
        heldItemContext: HeldItemContext?,
        seed: Int
    ) {
        itemModel.update(state, stack, resolver, displayContext, world, heldItemContext, seed)
        state.culling = !disableCulling
    }

    class Unbaked(
        val model: ItemModel.Unbaked,
        val disableCulling: Boolean
    ) : ItemModel.Unbaked {
        override fun getCodec(): MapCodec<out ItemModel.Unbaked> { throw AssertionError() }

        override fun bake(context: ItemModel.BakeContext): ItemModel {
            return EngineItemModel(
                model.bake(context),
                disableCulling
            )
        }

        override fun resolve(resolver: ResolvableModel.Resolver?) {
            model.resolve(resolver)
        }
    }
}