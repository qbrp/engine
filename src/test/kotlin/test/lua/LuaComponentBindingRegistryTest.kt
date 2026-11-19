package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.lua.library.LuaCachedComponentBinding
import org.lain.engine.script.lua.library.LuaComponentBindingRegistry
import org.lain.engine.script.lua.library.MassBinding
import org.lain.engine.script.lua.library.ecs.LocationBinding
import org.lain.engine.test.DummyWorld
import org.lain.engine.test.EngineTest
import org.lain.engine.util.ecs.ComponentTypeRegistry
import org.luaj.vm2.LuaTable

class LuaComponentBindingRegistryTest : EngineTest() {
    @Test
    fun bootstrapCreatesRegisteredRuntimeSlotsOnlyForCachedBindings() {
        val bindings = LuaComponentBindingRegistry.listBindings()
        assertTrue(bindings.any { it.cache })
        assertFalse(ComponentTypeRegistry.isRegistered(LuaCachedComponentBinding::class))
        assertEquals(bindings.size, bindings.map { it.componentClass }.toSet().size)
        val cacheTypes = bindings.filter { it.cache }.map { requireNotNull(it.cacheComponentType) }
        assertEquals(cacheTypes.size, cacheTypes.map { it.id }.toSet().size)

        for (binding in bindings) {
            val type = binding.cacheComponentType
            if (binding.cache) {
                val entry = requireNotNull(ComponentTypeRegistry.get(requireNotNull(type).id))
                assertSame(type, entry.type)
                assertFalse(entry.meta.savable)
                assertFalse(entry.meta.networking)
            } else {
                assertNull(type)
            }
        }
    }

    @Test
    fun worldReregistrationPreservesCacheArrayAndStoredPresentation() {
        val world = DummyWorld()
        val type = requireNotNull(LuaComponentBindingRegistry.listBindings().first { it.cache }.cacheComponentType)
        val array = world.componentManager.getComponentArray(type)
        val cached = LuaCachedComponentBinding(0, LuaTable())
        val entity = world.addEntity()
        with(world) { entity.setComponent(cached, type) }

        world.registerComponentTypes(world.simulation.namespacedStorage)

        assertSame(array, world.componentManager.getComponentArray(type))
        assertSame(cached, with(world) { entity.getComponent(type) })
    }

    @Test
    fun factoriesExposeCacheConfigurationWithoutAllocatingEcsSlots() {
        val count = ComponentTypeRegistry.count

        val cached = LocationBinding(cache = true)
        val uncached = MassBinding(cache = false)

        assertTrue(cached.cache)
        assertFalse(uncached.cache)
        assertNull(cached.cacheComponentType)
        assertNull(uncached.cacheComponentType)
        assertEquals(count, ComponentTypeRegistry.count)
    }

    @Test
    fun registeringBindingsAfterBootstrapIsRejected() {
        assertThrows<IllegalStateException> {
            LuaComponentBindingRegistry.register(MassBinding())
        }
    }
}
