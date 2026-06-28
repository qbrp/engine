package org.lain.engine.storage

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import org.jetbrains.exposed.v1.jdbc.Database
import org.lain.cyberia.ecs.*
import org.lain.engine.container.AssignedSlot
import org.lain.engine.item.*
import org.lain.engine.player.*
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptContext
import org.lain.engine.server.EngineServer
import org.lain.engine.util.*
import org.lain.engine.util.component.EntityCommandBuffer
import org.lain.engine.util.math.Pos
import org.lain.engine.world.World
import org.lain.engine.world.WorldId
import java.util.concurrent.ConcurrentLinkedQueue

/////////////// LEGACY SAVING

@Serializable
data class PersistentItemData(val components: List<ItemData>)

@Serializable
sealed class ItemData {
    @Serializable data class Display(val name: ItemName?, val tooltip: ItemTooltip?, val assets: ItemAssets? = null) : ItemData()
    @Serializable data class Guns(val data: Gun, val display: GunDisplay?) : ItemData()
    @Serializable data class Sounds(val data: ItemSounds) : ItemData()

    @Serializable data class PhysicalParameters(val count: org.lain.engine.item.Count, val mass: org.lain.engine.item.Mass?) : ItemData()
    @Serializable data class Book(@SerialName("writeable") val writableLegacy: Writable? = null, val writable: Writable? = null) : ItemData()
    @Serializable data class Equipment(val hat: Boolean, val outfit: Outfit? = null) : ItemData()

    @Serializable data class Lights(val flashlight: Flashlight) : ItemData()
    @Serializable data class EntityComponents(val components: List<ItemData>) : ItemData()
    @Serializable data class Contained(val containedIn: String, val assignedSLot: AssignedSlot? = null) : ItemData()
    @Serializable data class Container(val persistentId: PersistentId) : ItemData()

    @Serializable @Deprecated("Использовать PhysicalParameters") data class Mass(val value: Float) : ItemData()
    @Serializable @Deprecated("Использовать PhysicalParameters") data class Count(val value: Int) : ItemData()
}

@OptIn(ExperimentalSerializationApi::class)
val ITEM_CBOR = Cbor { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
fun deserializeItemPersistentComponents(array: ByteArray): List<ItemData> {
    return ITEM_CBOR.decodeFromByteArray(array)
}

////////////////// LOAD
/**
 * @deprecated C 22.04.2026 предметы загружаются как обычные сущности и не нуждаются в отдельном пайплайне
 */
fun WriteComponentAccess.loadItemLegacy(data: PersistentItemData, id: ItemId, uuid: PersistentId): EngineItem {
    val components = mutableSetOf<Component>()
    val entityComponents = mutableSetOf<Component>()
    var count: Count? = null
    var assets: ItemAssets? = null
    var tooltip: ItemTooltip? = null
    var name: ItemName? = null

    fun processComponent(component: ItemData) {
        when(component) {
            is ItemData.Display -> {
                name = component.name
                tooltip = component.tooltip
                assets = component.assets
            }
            is ItemData.Guns -> {
                components.addIfNotNull(component.data)
                components.addIfNotNull(component.display)
            }
            is ItemData.PhysicalParameters -> {
                components.addIfNotNull(component.count)
                components.addIfNotNull(component.mass)
            }
            is ItemData.Equipment -> {
                components.addIfNotNull(
                    component.outfit
                        ?: if (component.hat) {
                            Outfit(
                                EquipmentSlot.CAP,
                                OutfitDisplay.Separated,
                                listOf(PlayerPart.HEAD)
                            )
                        } else {
                            null
                        }
                )
            }
            is ItemData.Sounds ->
                components.addIfNotNull(component.data)
            is ItemData.Book ->
                components.add(component.writable ?: component.writableLegacy ?: error("Writeable component doesn't exist"))
            is ItemData.Lights ->
                components.add(component.flashlight)
            is ItemData.Count -> {
                count = Count(component.value, 16)
            }
            is ItemData.Mass ->
                components.add(Mass(component.value))
            // Контейнеры в легаси-системе не поддерживаются
            is ItemData.Contained -> {}
            is ItemData.Container -> {}
            is ItemData.EntityComponents -> component.components.forEach { processComponent(it) }
        }
    }
    data.components.forEach { processComponent(it) }

    return createItem(
        ItemPrefab(
            id,
            count?.max ?: 1,
            name?.text ?: "Предмет",
            assets ?: ItemAssets(mutableMapOf()),
            ItemProgressionAnimations(mutableMapOf()),
            { tooltip },
            { components.toList() + entityComponents.toList() }
        ),
        uuid
    )
}

context(read: ReadComponentAccess, write: WriteComponentAccess)
fun dataFixItem(item: EngineItem, storage: NamespacedStorageAccess) {
    val prefabId = item.requireComponent<Item>().id
    val prefab = storage.items[prefabId] ?: return
    if (!item.hasComponent<ItemAssets>()) {
        val assets = prefab.assets
        assets?.let { item.setComponent(it) }
    }
    if (!item.hasComponent<ItemProgressionAnimations>()) {
        val animations = prefab.progressionAnimations
        animations?.let { item.setComponent(it) }
    }
    if (!item.hasComponent<Count>()) {
        item.setComponent(Count(1, 1))
    }
    if (!item.hasComponent<ItemName>()) {
        item.setComponent(ItemName("Предмет"))
    }
}

// Для логирования
sealed class ItemLoadContext {
    abstract fun append(data: MutableMap<String, in Any>)
    fun data(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        append(map)
        return map
    }

    data class PreparingPlayer(val playerId: PlayerId, val name: String) : ItemLoadContext() {
        override fun append(data: MutableMap<String, in Any>) {
            data["context"] = "preparing_player"
            data["player_id"] = playerId.toString()
            data["player_name"] = name
        }
    }
    data class FromInventory(val player: PlayerId?, val voxelPos: Pos?) : ItemLoadContext() {
        override fun append(data: MutableMap<String, in Any>) {
            data["context"] = "from_inventory"
            data["player_name"] = player.toString()
            data["voxel_pos"] = voxelPos.toString()
        }
    }
}

class ItemLoader(
    private val server: EngineServer,
    private val database: Database
) {
    private val commandBuffers = ConcurrentLinkedQueue<Pair<WorldId, EntityCommandBuffer>>()

    suspend fun loadWorldItem(
        uuid: PersistentId,
        world: World,
        context: ItemLoadContext
    ): EngineItem {
        require(uuid is Uuid)
        world.itemStorage.get(uuid)?.let { item -> return item }
        val commandBuffer = EntityCommandBuffer(world)
        val entityResolver = DatabaseEntityResolver(database)
        return with(commandBuffer) {
            // Первый блок отвечает за обработку предмета в целом. Если что-то пойдет не так - это высветится в консоль и будет выдан недействительный предмет
            val entity = runCatching {
                // Функция загрузки сущности может выбрасывать ошибки, если предмет был в старой базе данных
                // Точно не знаю, с чем связано - в консоль бросается:
                // kotlinx.serialization.MissingFieldException: Field 'value' is required for type with serial name 'org.lain.engine.storage.PersistentId', but it was missing
                // (в EntityPersistent.deserializeEntityComponents)
                // Если есть исключение в базе данных - ищем предмет в старой, если и там нет, выбрасываем ошибку
                val rawEntity = runCatching { database.loadEntity(uuid) }
                rawEntity
                    .getOrNull()
                    ?.let { components ->
                        // сущность автоматически добавляется в ItemStorage
                        entityResolver.loadEntity(world.componentLoadSettings, components, uuid)
                    }
                    ?: database.loadPersistentItemDataLegacy(uuid)?.let { (id, components) ->
                        loadItemLegacy(components, id, uuid)
                    }
                    ?: run {
                        val e = rawEntity.exceptionOrNull()
                        if (e != null) throw e
                        null
                    }
            }
                 .onFailure { err ->
                     LOGGER.error("Не удалось загрузить предмет $uuid", err)
                     EngineLogger.log(
                         Log(
                             LogMessages.ITEM_LOAD_ERROR,
                             LogLevel.ERROR,
                             error = err,
                             data = mutableMapOf(
                                 "uuid" to uuid.toString(),
                             ).also { context.append(it as MutableMap<String, in Any>) },
                             tick = server.tick,
                             world = world.id
                         )
                     )
                 }
                .getOrNull()
                ?: server.createInvalidItem(world)

            commandBuffers += world.id to commandBuffer
            server.logInMainThread(world) { tick ->
                // для удобства выполняем другие операции здесь, т.к. функция работает также, как и EntityResolver.schedule
                server.callbacks.itemLoad.execute(ScriptContext.ItemLoad(world, entity))

                Log(
                    LogMessages.ITEM_LOAD,
                    LogLevel.INFO,
                    mutableMapOf(
                        "uuid" to uuid.toString(),
                        "id" to entity.requireComponent<Item>().id.toString(),
                        "components" to world.componentManager.getComponentsMap(entity).keys.map { it.id },
                        "linked_entities" to entityResolver.resolved.map { it.getEntityDebugNameId().name }
                    ).also { context.append(it) },
                    world = world.id,
                    tick = tick
                )
            }

            entity
        }
    }

    fun applyCommands(world: World) {
        commandBuffers.flush { (worldId, buffer) ->
            if (world.id == worldId) buffer.apply(world)
        }
    }
}