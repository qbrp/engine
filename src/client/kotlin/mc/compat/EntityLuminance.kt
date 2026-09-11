package org.lain.engine.client.mc.compat

import com.mojang.serialization.MapCodec
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.Entity
import org.jetbrains.annotations.Range
import org.lain.engine.mc.engineId

object EngineEntityLuminance : EntityLuminance {
    override fun type(): EntityLuminance.Type {
        return EntityLuminance.Type.registerSimple(
            engineId("luminance"), EngineEntityLuminance
        )
    }

    override fun getLuminance(
        itemLightSourceManager: ItemLightSourceManager,
        entity: Entity
    ): @Range(from = 0, to = 15) Int {
        return entity.get(ENGINE_ENTITY_LUMINANCE_COMPONENT) ?: 0
    }

}

val ENGINE_ENTITY_LUMINANCE_COMPONENT = Registry.register(
    BuiltInRegistries.DATA_COMPONENT_TYPE,
    engineId("luminance"),
    DataComponentType
        .builder<Int>()
        .persistent(MapCodec.unitCodec { 0 })
        .build()
)

// ленивая инициализация
fun registerEngineLightComponents() = Unit

fun registerLamdDynLightEvents(context: DynamicLightsContext) {
    context.entityLightSourceManager().onRegisterEvent().register { ctx ->
        val allEntities = ctx.registryLookup()
            .lookupOrThrow(Registries.ENTITY_TYPE)
            .listElements()
            .map { it.value() }
            .toList()
        allEntities.forEach {
            ctx.register(it, EngineEntityLuminance)
        }
    }
}
