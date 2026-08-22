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
import org.lain.engine.script.CompilationResult
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptEngine
import org.lain.engine.script.ScriptSystemDispatcher
import org.lain.engine.script.loadCompilationResult
import org.lain.engine.script.registerComponentTypes
import org.lain.engine.script.scriptContext
import org.lain.engine.server.ServerPlatform
import org.lain.engine.server.confirmProcessedPlayerInputsSystem
import org.lain.engine.storage.PersistentCharacterData
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
    val playerInputMode: PlayerInputMode
) {
    @Volatile
    var ticks = 0L

    var callbacks = Callbacks()
    val scriptSystemDispatcher = ScriptSystemDispatcher()

    private val _worlds: MutableMap<WorldId, World> = mutableMapOf()
    val worlds: Map<WorldId, World>
        get() = _worlds
    val defaultWorld
        get() = worlds.values.first()
    val worldsList
        get() = worlds.values

    private val playerInputSystem = PlayerInputSystem(playerInputMode)

    fun loadWorld(world: World) {
        world.registerComponentTypes(namespacedStorage)
        _worlds[world.id] = world
        scriptEngine.loadWorld(world)
    }

    fun assertOnThread(): Boolean {
        return Thread.currentThread() === thread
    }

    fun applyCompilationResult(result: CompilationResult) {
        result.callbacks?.let { callbacks = it }
        namespacedStorage.loadCompilationResult(result)
        worldsList.forEach { it.registerComponentTypes(namespacedStorage) }
        scriptSystemDispatcher.load(result.phases, namespacedStorage)
    }

    fun World.tick(): Unit = with(extension) {
        // Фаза подготовки данных
        beforeInput()
        resetItemOwnershipState()
        tickItemOwnershipSystem()
        tickPlayerModelSystem()

        // Геймплейная фаза
        tickMovementSystem(settings.movementDefaultAttributes, settings.movementSettings)

        // Взаимодействия
        playerInputSystem.tick(this@tick, callbacks) // здесь клиент начинает предсказывать поведение симуляции
        afterInput()

        tickGunActionSystem()
        tickSocialActionSystem()
        tickWritableActionSystem()

        // Оружейные системы
        tickMagazineActionSystem()
        tickGunSystem()
        tickRecoilSystem()

        afterInteractions()

        // Скрипты

        scriptEngine.tickBeforeCallbacks(this@tick)
        tickCallbacks(callbacks)
        scriptSystemDispatcher.tick(this@tick)
        scriptEngine.tick(this@tick)

        // Обработка накопленных операций
        tickNarrationSystem()
        tickContainerOperationsSystem() // Когда чтение данных контейнеров точно не будет, вызываем систему операций

        afterOperations()

        confirmProcessedPlayerInputsSystem()

        beforeEventCleanup()

        clearEvents()
        ticks++
    }

    fun instantiatePlayer(
        player: EnginePlayer,
        engineCharacter: EngineCharacter? = null,
        characterPersistentCharacter: PersistentCharacterData? = null,
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
        val ownedItems = player.collectOwnedItems() + player.items
        ownedItems.forEach { item ->
            item.removeComponent<HeldBy>()
        }

        player.equipmentContainer.destroy()
        player.mainContainer.destroy()
        player.entity.destroy()
    }

    fun tick() {
        worldsList.forEach {
            it.tick()
        }
        extension.afterTick(this)
    }

    interface SimulationTickExtension {
        fun World.beforeInput() {}
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
        val movementSettings: MovementSettings
        val movementDefaultAttributes: MovementDefaultAttributes

        companion object {
            val DUMMY = object : Settings {
                override val movementSettings: MovementSettings = MovementSettings()
                override val movementDefaultAttributes: MovementDefaultAttributes =
                    MovementDefaultAttributes()
            }
        }
    }
}
