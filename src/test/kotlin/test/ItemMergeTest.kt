package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.markDirty
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.requireComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.data.PersistentIdComponent
import org.lain.engine.item.Count
import org.lain.engine.item.EngineItem
import org.lain.engine.item.ItemName
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.Magazine
import org.lain.engine.item.Writable
import org.lain.engine.item.createItem
import org.lain.engine.item.merge
import org.lain.engine.item.toItemPrefabId
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.EngineId
import org.lain.engine.script.ScriptComponentType
import org.lain.engine.script.lua.LuaScriptComponent
import org.lain.engine.server.replication.Changes
import org.lain.engine.test.lua.LuaScriptEngineTestBase
import org.lain.engine.util.DebugName
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

class ItemMergeTest : LuaScriptEngineTestBase() {
    private lateinit var world: World
    private val lua by lazy { createLuaEngine("lua/component_state.lua") }
    private val prefab = DummyItemPrefab().copy(
        id = EngineId("test/stackable").toItemPrefabId(),
        maxCount = 64,
        assets = null,
        progressionAnimations = null,
    )
    private val ammunition = EngineId("test/ammunition").toItemPrefabId()

    @BeforeEach
    fun setUp() {
        world = World(WorldId("item-merge-test"), TestEngineSimulation())
    }

    @Test
    fun matchingItemsWithDifferentPersistentIdsAreCompatible() = with(world) {
        val first = item()
        val second = item()

        assertNotEquals(
            first.requireComponent<PersistentIdComponent>().id,
            second.requireComponent<PersistentIdComponent>().id,
        )
        assertCompatible(first, second)
    }

    @Test
    fun differentStackCountsAreCompatibleAndCheckingDoesNotChangeEitherItem() = with(world) {
        val first = item(count = 3)
        val second = item(count = 8)
        val forward = merge(first, second)
        val backward = merge(second, first)

        assertAll(
            { assertTrue(forward) },
            { assertTrue(backward) },
            { assertEquals(Count(3, 64), first.requireComponent<Count>()) },
            { assertEquals(Count(8, 64), second.requireComponent<Count>()) },
            { assertTrue(exists(first)) },
            { assertTrue(exists(second)) },
        )
    }

    @Test
    fun differentItemKindsAreNotCompatible() = with(world) {
        val first = item()
        val second = item(prefab = prefab.copy(id = EngineId("test/other").toItemPrefabId()))
        second.setComponent(first.requireComponent<DebugName>())

        assertIncompatible(first, second)
    }

    @Test
    fun itemsWithoutCountAreNotCompatible() = with(world) {
        val first = item()
        val second = item()

        first.removeComponent<Count>()
        assertIncompatible(first, second)
        second.removeComponent<Count>()
        assertIncompatible(first, second)
    }

    @Test
    fun differentMagazineContentsAreNotCompatible() = with(world) {
        val first = item(count = 3)
        val second = item(count = 3)
        first.setComponent(Magazine(30, 5, ammunition))
        second.setComponent(Magazine(30, 9, ammunition))

        assertIncompatible(first, second)
    }

    @Test
    fun differentMagazineCapacityOrAmmunitionIsNotCompatible() = with(world) {
        val first = item()
        val second = item()
        first.setComponent(Magazine(30, 5, ammunition))
        second.setComponent(Magazine(20, 5, ammunition))
        assertIncompatible(first, second)

        second.setComponent(Magazine(30, 5, EngineId("test/other_ammunition").toItemPrefabId()))
        assertIncompatible(first, second)

        second.setComponent(Magazine(30, 5, ammunition))
        assertCompatible(first, second)
    }

    @Test
    fun differentBookContentsAreNotCompatible() = with(world) {
        val first = item()
        val second = item()
        first.setComponent(Writable(4, listOf("first page", "second page")))
        second.setComponent(Writable(4, listOf("first page", "different page")))

        assertIncompatible(first, second)
        second.requireComponent<Writable>().contents = listOf("first page", "second page")
        second.markDirty<Writable>()
        assertCompatible(first, second)
    }

    @Test
    fun statePresentOnOnlyOneItemPreventsMerging() = with(world) {
        val first = item()
        val second = item()
        first.setComponent(Magazine(30, 5, ammunition))

        assertIncompatible(first, second)
        second.setComponent(Magazine(30, 5, ammunition))
        assertCompatible(first, second)
    }

    @Test
    fun differentItemNamesAreNotCompatible() = with(world) {
        val first = item()
        val second = item()
        second.setComponent(ItemName("Renamed"))

        assertIncompatible(first, second)
        second.setComponent(first.requireComponent<ItemName>().copy())
        assertCompatible(first, second)
    }

    @Test
    fun replicationRevisionsDoNotChangeItemCompatibility() = with(world) {
        val first = item()
        val second = item()
        first.requireComponent<Changes>().revision = 1
        second.requireComponent<Changes>().revision = 8

        assertCompatible(first, second)
    }

    @Test
    fun luaStateIsComparedByNestedValues() = with(world) {
        val first = item()
        val second = item()
        val type = CoreScriptComponents.VOXEL_DOOR
        setLuaState(first, type, bullets = 5)
        setLuaState(second, type, bullets = 9)

        assertIncompatible(first, second)
        setLuaState(second, type, bullets = 5)
        assertEquals(first.getComponent(type)?.value, second.getComponent(type)?.value)
        assertCompatible(first, second)
    }

    @Test
    fun equalLuaValuesWithDifferentComponentTypesAreNotCompatible() = with(world) {
        val first = item()
        val second = item()
        val firstType = CoreScriptComponents.VOXEL_DOOR
        val secondType = CoreScriptComponents.LIGHT_SOURCE
        setLuaState(first, firstType, bullets = 5)
        setLuaState(second, secondType, bullets = 5)

        assertIncompatible(first, second)
        second.removeComponent(secondType)
        setLuaState(second, firstType, bullets = 5)
        assertCompatible(first, second)
    }

    @Test
    fun inPlaceLuaStateChangesAreObserved() = with(world) {
        val first = item()
        val second = item()
        val type = CoreScriptComponents.VOXEL_DOOR
        setLuaState(first, type, bullets = 5)
        val nested = setLuaState(second, type, bullets = 5)
        val matchingForward = merge(first, second)
        val matchingBackward = merge(second, first)

        nested.set("bullets", LuaValue.valueOf(9))
        componentManager.markDirty(second, type)
        val changedForward = merge(first, second)
        val changedBackward = merge(second, first)

        assertAll(
            { assertTrue(matchingForward) },
            { assertTrue(matchingBackward) },
            { assertFalse(changedForward) },
            { assertFalse(changedBackward) },
        )
    }

    private fun item(count: Int = 1, prefab: ItemPrefab = this.prefab): EngineItem = with(world) {
        createItem(prefab, coordinator = null).also { it.setComponent(Count(count, prefab.maxCount)) }
    }

    private fun setLuaState(item: EngineItem, type: ScriptComponentType, bullets: Int): LuaTable = with(world) {
        val nested = LuaTable().apply { set("bullets", LuaValue.valueOf(bullets)) }
        val value = LuaTable().apply { set("magazine", nested) }
        item.setComponent(LuaScriptComponent(value, type, lua), type)
        nested
    }

    private fun assertCompatible(first: EngineItem, second: EngineItem) = assertAll(
        { assertTrue(world.merge(first, second)) },
        { assertTrue(world.merge(second, first)) },
    )

    private fun assertIncompatible(first: EngineItem, second: EngineItem) = assertAll(
        { assertFalse(world.merge(first, second)) },
        { assertFalse(world.merge(second, first)) },
    )
}
