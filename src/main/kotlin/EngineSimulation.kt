package org.lain.engine

import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.exists
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.container.tickContainerOperationsSystem
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.player.interaction.*
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptSystemDispatcher
import org.lain.engine.script.compilation.loadBuild
import org.lain.engine.script.scriptContext
import org.lain.engine.server.replication.confirmProcessedPlayerInputsSystem
import org.lain.engine.util.ecs.destroyIfExists
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import org.lain.engine.world.tickRecoilSystem
import kotlin.let

class EngineSimulation(
    val isClient: Boolean = false,
    val extension: SimulationTickExtension,
    val settings: Settings,
    val players: PlayerStorage,
    val namespacedStorage: NamespacedStorageAccess,
    val scriptEngine: ScriptEngine,
    val thread: Thread,
    var useAdventureMode: Boolean,
) {
    @Volatile
    var ticks = 0L

    var callbacks = Callbacks()
    val scriptSystemDispatcher = ScriptSystemDispatcher()

    val worlds: Map<WorldId, World>
        field: MutableMap<WorldId, World> = mutableMapOf()
    val defaultWorld
        get() = worlds.values.first()
    val worldsList
        get() = worlds.values

    fun loadWorld(world: World) {
        world.registerComponentTypes(namespacedStorage)
        worlds[world.id] = world
        scriptEngine.loadWorld(world)
    }

    fun assertOnThread(): Boolean {
        return Thread.currentThread() === thread
    }

    fun applyCompilationResult(result: Build) {
        result.callbacks?.let { callbacks = it }
        namespacedStorage.loadBuild(result)
        worldsList.forEach { it.registerComponentTypes(namespacedStorage) }
        scriptSystemDispatcher.phases = result.phases
        scriptEngine.onCompiled(namespacedStorage)
    }

    fun World.tick(): Unit = with(extension) {
        val phases = scriptSystemDispatcher.phases

        // Фаза подготовки данных
        tickDaraPreparation()
        tickItemOwnershipSystem()
        tickInteractorLocations()
        tickPlayerHandSystem()
        tickPlayerModelSystem()

        // Симуляция
        beforeInteractions()
        tickInteractionPhase(callbacks, extension)
        afterInput()

        scriptEngine.tickPush(this@tick)

        tickCallbacks(callbacks)

        phases.verbLookup.tick(this@tick)
        scriptEngine.tickVerbLookup(this@tick)
        tickVerbLookupApply()

        phases.base.tick(this@tick)
        scriptEngine.tickPull(this@tick)

        tickGunActionSystem()
        tickSocialActionSystem()
        tickWritableActionSystem()

        tickMagazineActionSystem()
        tickGunSystem()
        tickRecoilSystem()

        // Операции

        afterInteractions()

        tickContainerOperationsSystem() // Когда чтение данных контейнеров точно не будет, вызываем систему операций

        afterOperations()

        confirmProcessedPlayerInputsSystem()
        cleanupInteractionPhase()

        beforeEventCleanup()

        clearEvents()
        ticks++
    }

    fun instantiatePlayer(player: EnginePlayer) = with(player.world) {
        this@EngineSimulation.players.add(player)
        players += player

        scriptEngine.setupPlayer(player)
        callbacks.of(CallbackType.PLAYER_INSTANTIATE)?.execute(player.scriptContext)
    }

    fun preparePlayerDestroy(player: EnginePlayer) = with(player.world) {
        this@EngineSimulation.players.remove(player)
        players -= player

        callbacks.of(CallbackType.PLAYER_DESTROY)?.execute(player.scriptContext)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        val inventory = player.require<PlayerInventory>()
        val ownedItems = player.equipment.values + inventory.items
        ownedItems.forEach { item ->
            if (item.exists()) item.removeComponent<HeldBy>()
        }

        player.mainContainer.destroyIfExists()
        inventory.mainHandInteractor.destroyIfExists()
        inventory.offHandInteractor.destroyIfExists()
        player.entity.destroyIfExists()
    }

    fun tick() {
        worldsList.forEach { it.tick() }
        extension.afterTick(this)
    }

    interface SimulationTickExtension {
        fun World.tickDaraPreparation() {}
        fun World.beforeInteractions() {}
        fun World.afterInput() {}
        fun World.afterInteractions() {}
        fun World.afterOperations() {}
        fun World.beforeEventCleanup() {}
        fun afterTick(simulation: EngineSimulation) {}

        companion object {
            val DUMMY = object : SimulationTickExtension {}
        }
    }

    interface Settings {
        companion object {
            val DUMMY = object : Settings {}
        }
    }
}
