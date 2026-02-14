package org.lain.engine.client.resources

import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.item.ItemModelManager
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.client.render.model.ResolvableModel
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.util.HeldItemContext
import org.joml.Vector3fc

private val CULLING = RenderStateDataKey.create<Boolean> { "Engine culling" }

var ItemRenderState.culling: Boolean?
    get() = this.getData(CULLING)
    set(value) { setData(CULLING, value) }

class EngineItemModel(
    val asset: Asset,
    val itemModel: ItemModel,
    val disableCulling: Boolean,
    val markers: Map<String, Vector3fc>
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
        val asset: Asset,
        val model: ItemModel.Unbaked,
        val disableCulling: Boolean,
        val markers: Map<String, Vector3fc>
    ) : ItemModel.Unbaked {
        override fun getCodec(): MapCodec<out ItemModel.Unbaked> { throw AssertionError() }

        override fun bake(context: ItemModel.BakeContext): ItemModel {
            return EngineItemModel(
                asset,
                model.bake(context),
                disableCulling,
                markers
            )
        }

        override fun resolve(resolver: ResolvableModel.Resolver?) {
            model.resolve(resolver)
        }
    }
}