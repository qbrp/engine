package org.lain.engine

import org.lain.cyberia.ecs.destroy
import org.lain.cyberia.ecs.removeComponent
import org.lain.engine.container.tickContainerOperationsSystem
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.player.character.EngineCharacter
import org.lain.engine.player.character.applyCharacter
import org.lain.engine.player.interaction.*
import org.lain.engine.script.CallbackType
import org.lain.engine.script.Callbacks
import org.lain.engine.script.compilation.Build
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptSystemDispatcher
import org.lain.engine.script.compilation.loadResult
import org.lain.engine.script.scriptContext
import org.lain.engine.server.ServerPlatform
import org.lain.engine.server.replication.confirmProcessedPlayerInputsSystem
import org.lain.engine.data.PersistentCharacterRecord
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
        namespacedStorage.loadResult(result)
        worldsList.forEach { it.registerComponentTypes(namespacedStorage) }
        scriptSystemDispatcher.phases = result.phases
    }

    fun World.tick(): Unit = with(extension) {
        val phases = scriptSystemDispatcher.phases

        // Фаза подготовки данных
        tickDaraPreparation()
        resetItemOwnershipState()
        tickItemOwnershipSystem()
        tickInteractorLocations()
        tickPlayerHandSystem()
        tickPlayerModelSystem()

        // Симуляция
        beforeInteractions()
        tickInteractionPhase(callbacks)
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

        tickNarrationSystem()
        tickContainerOperationsSystem() // Когда чтение данных контейнеров точно не будет, вызываем систему операций

        afterOperations()

        confirmProcessedPlayerInputsSystem()
        cleanupInteractionPhase()

        beforeEventCleanup()

        clearEvents()
        ticks++
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        engineCharacter: EngineCharacter? = null,
        characterPersistentCharacter: PersistentCharacterRecord? = null,
        platform: ServerPlatform? = null,
    ) = with(player.world) {
        this@EngineSimulation.players.add(player)
        players += player
        platform?.onPlayerInstantiated(player)

        scriptEngine.setupPlayer(player)
        engineCharacter?.let {
            player.applyCharacter(engineCharacter, characterPersistentCharacter, platform!!)
        }
        callbacks.of(CallbackType.PLAYER_INSTANTIATE)?.execute(player.scriptContext)
    }

    fun preparePlayerDestroy(player: EnginePlayer) = with(player.world) {
        this@EngineSimulation.players.remove(player)
        player.destroyed = true
        players -= player

        callbacks.of(CallbackType.PLAYER_DESTROY)?.execute(player.scriptContext)
    }

    fun destroyPlayer(player: EnginePlayer) = with(player.world) {
        val inventory = player.require<PlayerInventory>()
        val ownedItems = player.equipment.values + inventory.items
        ownedItems.forEach { item -> item.removeComponent<HeldBy>() }

        player.mainContainer.destroy()
        inventory.mainHandInteractor.destroy()
        inventory.offHandInteractor.destroy()
        player.entity.destroy()
    }

    fun tick() {
        worldsList.forEach {
            it.tick()
        }
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
