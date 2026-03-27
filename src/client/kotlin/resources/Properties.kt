package org.lain.engine.client.resources

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.item.ItemModelManager
import net.minecraft.client.render.item.ItemRenderState
import net.minecraft.client.render.item.model.ItemModel
import net.minecraft.client.render.model.ResolvableModel
import net.minecraft.client.render.model.json.Transformation
import net.minecraft.client.world.ClientWorld
import net.minecraft.component.ComponentType
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.util.HeldItemContext
import org.joml.Vector3fc
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.render.EngineItemDisplayContext
import org.lain.engine.mc.ITEM_STACK_MATERIAL
import org.lain.engine.player.Outfit
import org.lain.engine.util.EngineId

private val CULLING = RenderStateDataKey.create<Boolean> { "Engine culling" }

var ItemRenderState.culling: Boolean?
    get() = this.getData(CULLING)
    set(value) { setData(CULLING, value) }

private val ENGINE_TRANSFORMATION = RenderStateDataKey.create<Transformation> { "Engine transformation" }

var ItemRenderState.engineTransformation: Transformation?
    get() = this.getData(ENGINE_TRANSFORMATION)
    set(value) { setData(ENGINE_TRANSFORMATION, value) }

data class OutfitTag(val outfit: Outfit) {
    companion object {
        val TYPE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            EngineId("outfit-tag"),
            ComponentType
                .builder<OutfitTag>()
                .codec(
                    Codec.unit(OutfitTag(Outfit ()))
                )
                .build()
        )
    }
}

class EngineItemModel(
    val asset: Asset,
    val itemModel: ItemModel,
    val disableCulling: Boolean,
    val markers: Map<String, Vector3fc>,
    val outfitTransformation: Transformation?
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
        val outfitTag = stack.get(OutfitTag.TYPE)
        if (outfitTag != null && outfitTransformation != null) {
            state.engineTransformation = outfitTransformation
        }
    }

    fun updateEngine(
        state: ItemRenderState,
        stack: ItemStack?,
        displayContext: EngineItemDisplayContext,
        world: ClientWorld?,
        heldItemContext: HeldItemContext?,
        seed: Int
    ) {
        itemModel.update(
            state,
            stack ?: ITEM_STACK_MATERIAL,
            MinecraftClient.itemModelManager,
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
        val outfitTransformation: Transformation?
    ) : ItemModel.Unbaked {
        override fun getCodec(): MapCodec<out ItemModel.Unbaked> { throw AssertionError() }

        override fun bake(context: ItemModel.BakeContext): ItemModel {
            return EngineItemModel(
                asset,
                model.bake(context),
                disableCulling,
                markers,
                outfitTransformation
            )
        }

        override fun resolve(resolver: ResolvableModel.Resolver?) {
            model.resolve(resolver)
        }
    }
}