package org.lain.engine.test

import org.junit.jupiter.api.BeforeAll
import org.lain.engine.bootstrap
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.util.component.registerAll

//fun DummyWorld() = World(
//    WorldId("dummy"),
//    ComponentWorld(Thread.currentThread()),
//    namespacedStorage = ThreadSafeNamespaceStorageAccessImpl(emptyNamespacedStorage())
//)
//
//fun DummyLocation(world: World) = Location(world, Vec3(0f))

//fun World.dummyLocation() = DummyLocation(this)

fun DummyItemPrefab() = ItemPrefab(
    ItemId("dummy"),
    1,
    "Dummy Item Name",
    ItemAssets(mapOf("default" to "dummy")),
    ItemProgressionAnimations(mapOf()),
    { null },
    { emptyList() }
)

abstract class EngineTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            bootstrap()
        }
    }
}