package org.lain.engine.test

import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.util.component.ComponentWorld
import org.lain.engine.util.math.Vec3
import org.lain.engine.world.Location
import org.lain.engine.world.World
import org.lain.engine.world.WorldId

fun DummyWorld() = World(WorldId("dummy"), ComponentWorld(Thread.currentThread()))

fun DummyLocation(world: World) = Location(world, Vec3(0f))

fun World.dummyLocation() = DummyLocation(this)

fun DummyItemPrefab() = ItemPrefab(
    ItemId("dummy"),
    1,
    "Dummy Item Name",
    ItemAssets(mapOf("default" to "dummy")),
    ItemProgressionAnimations(mapOf()),
    { emptyList() }
)