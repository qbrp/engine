package org.lain.engine.test

import org.junit.jupiter.api.BeforeAll
import org.lain.cyberia.ecs.componentTypeOf
import org.lain.cyberia.ecs.hasComponent
import org.lain.cyberia.ecs.iterate
import org.lain.cyberia.ecs.removeComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.EngineSimulation
import org.lain.engine.bootstrap
import org.lain.engine.container.EntriesDirty
import org.lain.engine.item.ItemAssets
import org.lain.engine.item.ItemId
import org.lain.engine.item.ItemPrefab
import org.lain.engine.item.ItemProgressionAnimations
import org.lain.engine.player.PlayerStorage
import org.lain.engine.player.interaction.PlayerInputMode
import org.lain.engine.script.EngineId
import org.lain.engine.script.NamespacedStorage
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.server.replication.Networked
import org.lain.engine.util.Storage
import org.lain.engine.util.ecs.ComponentWorld
import org.lain.engine.util.ecs.EntityId
import org.lain.engine.util.math.MutableEVec3
import org.lain.engine.world.Location
import java.util.concurrent.ConcurrentHashMap

fun TestEngineSimulation(
    isClient: Boolean = false,
    tickExtension: EngineSimulation.SimulationTickExtension = EngineSimulation.SimulationTickExtension.DUMMY,
    settings: EngineSimulation.Settings = EngineSimulation.Settings.DUMMY,
    namespacedStorage: NamespacedStorageAccess = ThreadSafeNamespaceStorageAccessImpl(NamespacedStorage())
) = EngineSimulation(
    isClient,
    tickExtension,
    settings,
    PlayerStorage(),
    namespacedStorage,
    ScriptEngine.Dummy,
    Thread.currentThread(),
    PlayerInputMode.Authoritative
)

fun TestComponentWorld(
    registerEngineKotlinComponents: Boolean = false
): ComponentWorld {
    return ComponentWorld(
        Thread.currentThread(),
        ConcurrentHashMap(),
        Storage(),
        registerEngineKotlinComponents
    )
}

fun DummyItemPrefab() = ItemPrefab(
    ItemId(EngineId("dummy")),
    1,
    "Dummy Item Name",
    ItemAssets.withDefaultAsset(EngineId("dummy")),
    ItemProgressionAnimations(mapOf()),
    {}
)

class DenseEntityQueue(capacity: Int) {
    val entities = arrayOfNulls<EntityId>(capacity)

    var size: Int = 0
        private set

    fun add(entity: EntityId) {
        entities[size++] = entity
    }

    fun reset() {
        size = 0
    }
}

fun main() {
    bootstrap()

    with(TestComponentWorld(true)) {
        repeat(10_000) {
            val entity = addEntity()
            entity.setComponent(Location(MutableEVec3()))
        }

        /*
         * Прогрев обычного Location iteration.
         */
        repeat(1_000) {
            iterate<Location> { _, location ->
                location.position.mutateAdd(4f, 4f, 4f)
            }
        }

        /*
         * ------------------------------------------------------------
         * TEST 1
         *
         * Просто iteration + изменение существующего компонента.
         * ------------------------------------------------------------
         */

        val results = mutableListOf<Long>()

        repeat(100) {
            val start = System.nanoTime()

            iterate<Location> { _, location ->
                location.position.mutateAdd(4f, 4f, 4f)
            }

            results += System.nanoTime() - start
        }

        var checksum = 0f

        iterate<Location> { _, location ->
            checksum += location.position.x
        }

        val average = results.average()

        println(
            "average without operations: ${average / 1_000_000.0} ms"
        )

        println(
            "checksum: $checksum"
        )

        /*
         * ComponentType lookup делаем заранее, чтобы он не влиял
         * на сравнение immediate и queued вариантов.
         */

        val networkedType = componentTypeOf(Networked::class)
        val locationType = componentTypeOf(Location::class)
        val entriesDirtyType = componentTypeOf(EntriesDirty::class)

        /*
         * ------------------------------------------------------------
         * TEST 2
         *
         * Structural operations применяются непосредственно
         * во время iteration.
         * ------------------------------------------------------------
         */

        fun runImmediateOperations() {
            iterate<Location> { e, _ ->
                e.setComponent(
                    Networked,
                    networkedType
                )

                e.hasComponent(locationType)
            }

            iterate<Networked> { e, _ ->
                e.setComponent(
                    EntriesDirty,
                    entriesDirtyType
                )

                e.removeComponent<Networked>(
                    networkedType
                )
            }
        }

        /*
         * Прогрев immediate-варианта отдельно.
         */
        repeat(1_000) {
            runImmediateOperations()
        }

        val results2 = mutableListOf<Long>()

        repeat(1_000) {
            val start = System.nanoTime()

            runImmediateOperations()

            results2 += System.nanoTime() - start
        }

        val average2 = results2.average()

        println(
            "average with immediate operations: ${average2 / 1_000_000.0} ms"
        )

        /*
         * ------------------------------------------------------------
         * TEST 3
         *
         * Structural operations сначала записываются
         * в плотные очереди.
         *
         * Затем одинаковые операции применяются подряд:
         *
         * Add Networked:
         *     entity 0
         *     entity 1
         *     entity 2
         *     ...
         *
         * Set EntriesDirty:
         *     entity 0
         *     entity 1
         *     entity 2
         *     ...
         *
         * Remove Networked:
         *     entity 0
         *     entity 1
         *     entity 2
         *     ...
         *
         * Никаких Command-объектов и захватывающих лямбд.
         * ------------------------------------------------------------
         */

        val addNetworkedQueue =
            DenseEntityQueue(10_000)

        val setEntriesDirtyQueue =
            DenseEntityQueue(10_000)

        val removeNetworkedQueue =
            DenseEntityQueue(10_000)

        fun runQueuedOperations() {
            /*
             * Собираем Add<Networked>.
             */
            iterate<Location> { e, _ ->
                addNetworkedQueue.add(e)

                e.hasComponent(locationType)
            }

            /*
             * Flush Add<Networked>.
             *
             * Теперь вся серия операций работает с одним
             * component storage.
             */
            for (i in 0 until addNetworkedQueue.size) {
                val entity =
                    addNetworkedQueue.entities[i]!!

                entity.setComponent(
                    Networked,
                    networkedType
                )
            }

            addNetworkedQueue.reset()

            /*
             * Networked уже физически добавлены, поэтому query
             * теперь их видит.
             *
             * Здесь только собираем следующие structural changes.
             */
            iterate<Networked> { e, _ ->
                setEntriesDirtyQueue.add(e)
                removeNetworkedQueue.add(e)
            }

            /*
             * Сначала полностью обрабатываем EntriesDirty storage.
             */
            for (i in 0 until setEntriesDirtyQueue.size) {
                val entity =
                    setEntriesDirtyQueue.entities[i]!!

                entity.setComponent(
                    EntriesDirty,
                    entriesDirtyType
                )
            }

            setEntriesDirtyQueue.reset()

            /*
             * И только потом полностью обрабатываем
             * Networked storage.
             */
            for (i in 0 until removeNetworkedQueue.size) {
                val entity =
                    removeNetworkedQueue.entities[i]!!

                entity.removeComponent<Networked>(
                    networkedType
                )
            }

            removeNetworkedQueue.reset()
        }

        /*
         * Отдельный прогрев queued-варианта.
         */
        repeat(1_000) {
            runQueuedOperations()
        }

        val results3 = mutableListOf<Long>()

        repeat(1_000) {
            val start = System.nanoTime()

            runQueuedOperations()

            results3 += System.nanoTime() - start
        }

        val average3 = results3.average()

        println(
            "average with dense queued operations: ${average3 / 1_000_000.0} ms"
        )

        /*
         * ------------------------------------------------------------
         * Сравнение
         * ------------------------------------------------------------
         */

        println()

        println(
            "immediate / queued: ${average2 / average3}x"
        )

        println(
            "queued / immediate: ${average3 / average2}x"
        )
    }
}

abstract class EngineTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll(): Unit {
            bootstrap()
        }
    }
}