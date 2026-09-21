package org.lain.engine.client.mc.compat

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager
import net.minecraft.core.registries.Registries
import net.minecraft.world.entity.Entity
import org.jetbrains.annotations.Range
import org.lain.engine.mc.engineId
import java.util.Collections
import java.util.WeakHashMap

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
        return ENGINE_ENTITY_LUMINANCE[entity] ?: 0
    }

}

private val ENGINE_ENTITY_LUMINANCE = Collections.synchronizedMap(WeakHashMap<Entity, Int>())

fun Entity.setEngineLuminance(value: Int) {
    if (value <= 0) {
        ENGINE_ENTITY_LUMINANCE.remove(this)
    } else {
        ENGINE_ENTITY_LUMINANCE[this] = value.coerceIn(0, 15)
    }
}

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
