package org.lain.engine.test.lua

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.EntityId
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.item.ItemName
import org.lain.engine.item.Mass
import org.lain.engine.player.MovementStatus
import org.lain.engine.player.PlayerAttributes
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.inspectionSnapshot
import org.lain.engine.script.lua.LuaScriptEngine
import org.lain.engine.script.lua.library.LuaCachedComponentBinding
import org.lain.engine.script.lua.library.LuaComponentBinding
import org.lain.engine.script.lua.library.LuaComponentBindingId
import org.lain.engine.script.lua.library.LuaComponentBindingRegistry
import org.lain.engine.script.lua.library.checkLuaEntity
import org.lain.engine.script.lua.library.ecs.ItemNameBinding
import org.lain.engine.script.lua.library.ecs.LocationBinding
import org.lain.engine.script.lua.library.ecs.MovementStatusBinding
import org.lain.engine.script.lua.library.ecs.PlayerAttributesBinding
import org.lain.engine.script.lua.library.luaEntity
import org.lain.engine.script.lua.library.luaWorld
import org.lain.engine.script.lua.library.setLuaScriptComponent
import org.lain.engine.script.lua.loadScript
import org.lain.engine.test.DummyWorld
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class LuaComponentBindingCacheTest : LuaScriptEngineTestBase() {
    private lateinit var lua: LuaScriptEngine
    private lateinit var world: World
    private lateinit var binding: CountingMassBinding
    private lateinit var iterateFunction: LuaFunction

    @BeforeEach
    fun setup() {
        lua = createLuaEngine("lua/component_binding_cache.lua")
        world = DummyWorld()
        lua.loadWorld(world)
        binding = CountingMassBinding()
        iterateFunction = lua.globals.loadScript(lua.entrypoint).call().checkfunction()
    }

    @Test
    fun repeatedIterationInSameTickReusesBindingAndEntityHandle() {
        addEntity(10f)

        val first = iterate().single()
        val second = iterate().single()

        assertSame(first.components.single(), second.components.single())
        assertSame(first.entity, second.entity)
        assertEquals(1, binding.conversions)
    }

    @Test
    fun inspectionSkipsCachedPresentationsWithoutLookingUpTheirClass() {
        val entity = addEntity(10f)
        iterate()

        val snapshot = with(world) { entity.inspectionSnapshot() }

        assertTrue(snapshot.data.components.any { it.type == binding.componentType.id })
        assertTrue(snapshot.data.components.none { it.type == binding.cacheComponentType?.id })
    }

    @Test
    fun nextTickRebuildsPresentationFromCurrentComponent() {
        val entity = addEntity(10f)
        val first = iterate().single().components.single()
        with(world) { entity.getComponent<Mass>()!!.mass = 20f }
        world.simulation.ticks++

        val second = iterate().single().components.single()
        val repeated = iterate().single().components.single()

        assertNotSame(first, second)
        assertSame(second, repeated)
        assertEquals(10f, first.get("kg").tofloat())
        assertEquals(20f, second.get("kg").tofloat())
        assertEquals(2, binding.conversions)
    }

    @Test
    fun cacheIsIsolatedPerEntity() {
        val firstEntity = addEntity(10f)
        val secondEntity = addEntity(20f)

        val first = iterate().associateBy { it.entity.checkLuaEntity() }
        val repeated = iterate().associateBy { it.entity.checkLuaEntity() }
        val firstValue = first.getValue(firstEntity).components.single()
        val secondValue = first.getValue(secondEntity).components.single()

        assertNotSame(firstValue, secondValue)
        assertEquals(10f, firstValue.get("kg").tofloat())
        assertEquals(20f, secondValue.get("kg").tofloat())
        assertSame(firstValue, repeated.getValue(firstEntity).components.single())
        assertSame(secondValue, repeated.getValue(secondEntity).components.single())
        assertEquals(2, binding.conversions)
    }

    @Test
    fun worldsWithMatchingEntityIdsDoNotShareCachedBindings() {
        val otherWorld = World(WorldId("other"), world.simulation)
        lua.loadWorld(otherWorld)
        val firstEntity = addEntity(10f)
        val secondEntity = addEntity(20f, otherWorld)
        assertEquals(firstEntity, secondEntity)

        val first = iterate().single().components.single()
        val second = iterate(world = otherWorld).single().components.single()

        assertNotSame(first, second)
        assertEquals(10f, first.get("kg").tofloat())
        assertEquals(20f, second.get("kg").tofloat())
        assertSame(first, iterate().single().components.single())
        assertSame(second, iterate(world = otherWorld).single().components.single())
        assertEquals(2, binding.conversions)
    }

    @Test
    fun reusedEntitySlotDoesNotInheritTheDestroyedEntityCache() {
        val previousEntity = addEntity(10f)
        val previous = iterate().single()
        world.destroy(previousEntity)
        val replacement = addEntity(20f)
        assertEquals(previousEntity.index, replacement.index)

        val current = iterate().single()

        assertNotSame(previous.entity, current.entity)
        assertNotSame(previous.components.single(), current.components.single())
        assertEquals(replacement, current.entity.checkLuaEntity())
        assertEquals(20f, current.components.single().get("kg").tofloat())
        assertEquals(2, binding.conversions)
    }

    @Test
    fun bindingWithoutCacheConvertsOnEveryIterationAndEntityRead() {
        addEntity(10f)
        val uncached = CountingMassBinding(cached = false)

        val first = iterate(listOf(uncached)).single()
        val second = iterate(listOf(uncached)).single()
        val read = read(first.entity, binding = uncached)
        val required = read(first.entity, method = "require_component", binding = uncached)

        assertNotSame(first.components.single(), second.components.single())
        assertNotSame(second.components.single(), read)
        assertNotSame(read, required)
        assertEquals(4, uncached.conversions)
    }

    @Test
    fun entityReadsReuseThePresentationCachedByIteration() {
        addEntity(10f)
        val row = iterate().single()

        assertSame(row.components.single(), read(row.entity))
        assertSame(row.components.single(), read(row.entity, "require_component"))
        assertEquals(1, binding.conversions)
    }

    @Test
    fun iterationReusesThePresentationCachedByEntityRead() {
        val entity = luaEntity(addEntity(10f))
        val value = read(entity)

        val row = iterate().single()

        assertSame(entity, row.entity)
        assertSame(value, row.components.single())
        assertSame(value, read(entity, "require_component"))
        assertEquals(1, binding.conversions)
    }

    @Test
    fun allIterationAritiesCacheTheBindingInEveryQueryPosition() {
        val entity = addEntity(10f)
        with(world) {
            entity.setComponent(Location(Vec3(1, 2, 3)))
            entity.setComponent(MovementStatus())
            entity.setComponent(PlayerAttributes())
            entity.setComponent(ItemName("test"))
        }
        val otherBindings = listOf(LocationBinding(), MovementStatusBinding(), PlayerAttributesBinding(), ItemNameBinding())

        for (arity in 1..5) {
            for (position in 0 until arity) {
                world.simulation.ticks++
                val query = otherBindings.take(arity - 1).toMutableList<LuaComponentBinding<*>>()
                query.add(position, binding)
                val previousConversions = binding.conversions

                val first = iterate(query).single()
                val second = iterate(query).single()

                assertEquals(arity, first.components.size)
                assertEquals(10f, first.components[position].get("kg").tofloat())
                assertSame(first.components[position], second.components[position])
                assertEquals(previousConversions + 1, binding.conversions, "arity=$arity, position=$position")
            }
        }
    }

    @Test
    fun scriptComponentsKeepTheirLuaIdentityAlongsideCachedNativeBindings() {
        val entity = addEntity(10f)
        val scriptType = CoreScriptComponents.HAND
        val scriptValue = LuaTable().apply { set("value", LuaValue.valueOf(1)) }
        with(lua) { with(world) { entity.setLuaScriptComponent(scriptValue, scriptType) } }
        val scriptHolder = LuaTable().apply {
            set("access", lua.componentLibrary.scriptComponentAccess(scriptType))
        }
        val query = listOf(holder(binding), scriptHolder)

        val first = iterateHolders(query).single()
        scriptValue.set("value", LuaValue.valueOf(2))
        val second = iterateHolders(query).single()

        assertSame(scriptValue, first.components[1])
        assertSame(scriptValue, second.components[1])
        assertEquals(2, second.components[1].get("value").checkint())
        assertSame(first.components[0], second.components[0])
        assertEquals(1, binding.conversions)
    }

    @Test
    fun settingComponentThroughLuaInvalidatesItsSameTickCache() {
        addEntity(10f)
        val first = iterate().single()
        val replacement = LuaTable().apply { set("kg", LuaValue.valueOf(20)) }

        first.entity.get("set_component").call(first.entity, holder(binding), replacement)
        val current = iterate().single()

        assertNotSame(first.components.single(), current.components.single())
        assertEquals(20f, current.components.single().get("kg").tofloat())
        assertSame(current.components.single(), read(current.entity))
        assertEquals(3, binding.conversions)
    }

    @Test
    fun removingAnAlreadyCachedComponentActuallyRemovesIt() {
        val entity = addEntity(10f)
        val row = iterate().single()

        val removed = read(row.entity, "remove_component")

        assertSame(row.components.single(), removed)
        assertNull(with(world) { entity.getComponent<Mass>() })
        assertTrue(read(row.entity).isnil())
        assertTrue(iterate().isEmpty())
    }

    @Test
    fun removingComponentBeforeFirstReadDoesNotCacheAReadableRemovedValue() {
        val entity = addEntity(10f)
        val entityL = luaEntity(entity)

        val removed = read(entityL, "remove_component")

        assertEquals(10f, removed.get("kg").tofloat())
        assertNull(with(world) { entity.getComponent<Mass>() })
        assertTrue(read(entityL).isnil())
        assertTrue(read(entityL, "remove_component").isnil())
        assertTrue(iterate().isEmpty())
    }

    @Test
    fun missingComponentDoesNotCreateANegativeCacheEntry() {
        val entity = world.addEntity()
        val entityL = luaEntity(entity)
        assertTrue(read(entityL).isnil())
        assertTrue(iterate().isEmpty())
        assertEquals(0, binding.conversions)

        with(world) { entity.setComponent(Mass(10f)) }
        val value = read(entityL)

        assertEquals(10f, value.get("kg").tofloat())
        assertSame(value, iterate().single().components.single())
        assertEquals(1, binding.conversions)
    }

    private fun addEntity(mass: Float, world: World = this.world): EntityId = with(world) {
        world.addEntity { setComponent(Mass(mass)) }
    }

    private fun luaEntity(entity: EntityId): LuaValue = with(lua) { with(world) { entity.luaEntity() } }

    private fun holder(binding: LuaComponentBinding<*>): LuaValue = LuaTable().apply {
        set("access", lua.componentLibrary.bindingComponentAccess(binding))
    }

    private fun read(
        entity: LuaValue,
        method: String = "get_component",
        binding: LuaComponentBinding<*> = this.binding,
    ): LuaValue = entity.get(method).call(entity, holder(binding))

    private fun iterate(
        bindings: List<LuaComponentBinding<*>> = listOf(binding),
        world: World = this.world,
    ): List<Row> = iterateHolders(bindings.map(::holder), world)

    private fun iterateHolders(holders: List<LuaValue>, world: World = this.world): List<Row> {
        val worldL = with(lua) { world.luaWorld() }
        val rows = iterateFunction.invoke(LuaValue.varargsOf((listOf(worldL) + holders).toTypedArray()))
            .arg1().checktable()
        return (1..rows.length()).map { index ->
            val row = rows.get(index)
            val components = row.get("components").checktable()
            Row(row.get("entity"), (1..components.length()).map(components::get))
        }
    }

    private data class Row(val entity: LuaValue, val components: List<LuaValue>)

    private class CountingMassBinding(cached: Boolean = true) : LuaComponentBinding<Mass> {
        override val componentType: ComponentType<Mass> = componentTypeOf(Mass::class)
        override val cacheComponentType: ComponentType<LuaCachedComponentBinding>? =
            if (cached) {
                requireNotNull(LuaComponentBindingRegistry.get(LuaComponentBindingId(EngineId("core/mass"))).cacheComponentType)
            } else {
                null
            }
        override val id = EngineId("test/cached_mass")
        var conversions = 0
            private set

        context(lua: LuaScriptEngine)
        override fun toLua(world: World?, entity: EntityId, component: Mass): LuaValue {
            conversions++
            return LuaTable().apply { set("kg", LuaValue.valueOf(component.mass.toDouble())) }
        }

        context(lua: LuaScriptEngine)
        override fun fromLua(entity: EntityId, value: LuaValue): Mass = Mass(value.get("kg").tofloat())
    }

}
