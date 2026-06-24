package org.lain.engine.storage

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import org.lain.cyberia.ecs.*
import org.lain.engine.container.Entries
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.item.*
import org.lain.engine.script.*
import org.lain.engine.script.lua.createLuaScriptComponent
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toLuaArray
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.script.lua.toScriptValue
import org.lain.engine.server.Children
import org.lain.engine.server.Parent
import org.lain.engine.util.Storage
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.world.Luminance
import org.lain.engine.world.World
import java.util.LinkedList
import kotlin.reflect.KClass

class ComponentSerializerNotRegisteredException(val componentClass: KClass<out Any>) : Exception("Serializer not registered for component ${componentClass.simpleName}")

@OptIn(InternalSerializationApi::class)
@Suppress("UNCHECKED_CAST")
fun SerializersModuleBuilder.polymorphicComponentSubclasses() {
    val classes = ComponentTypeRegistry.listEntries()
        .filter { (_, entry) -> entry.meta.savable || entry.meta.networking }
        .mapNotNull { (_, entry) -> entry.meta.serializationClass }

    polymorphic(Component::class) {
        val exceptions = mutableListOf<ComponentSerializerNotRegisteredException>()
        classes.forEach {
            val serializer = runCatching { it.serializer() }
                .onFailure { exception ->
                    if (exception is SerializationException) {
                        exceptions += ComponentSerializerNotRegisteredException(it)
                    }
                }
                .getOrNull() as? KSerializer<Component>
            subclass(it as KClass<Component>, serializer ?: return@forEach)
        }
        if (exceptions.isNotEmpty()) {
            exceptions.forEach { LOGGER.error(it.message) }
            error("Serializer not registered for ${exceptions.map { it.componentClass }} components")
        }
    }
    polymorphic(ComponentData::class) {
        subclass(CopyComponentDto::class, CopyComponentDto.serializer())
        subclass(ScriptComponentDto::class, ScriptComponentDto.serializer())
        subclass(ParentComponentDto::class, ParentComponentDto.serializer())
        subclass(ChildrenComponentDto::class, ChildrenComponentDto.serializer())
        subclass(ContainedInDto::class, ContainedInDto.serializer())
        subclass(EntityRpcReceiverDto::class, EntityRpcReceiverDto.serializer())
    }
}

val COMPONENT_SERIALIZERS_MODULE = SerializersModule {
    polymorphicComponentSubclasses()
}

@OptIn(ExperimentalSerializationApi::class)
val COMPONENT_CBOR = Cbor {
    ignoreUnknownKeys = true
    serializersModule = COMPONENT_SERIALIZERS_MODULE
}

@OptIn(ExperimentalSerializationApi::class)
fun serializeEntityComponents(components: List<ComponentDto>): ByteArray {
    return COMPONENT_CBOR.encodeToByteArray(ListSerializer(serializer<ComponentDto>()), components)
}

@OptIn(ExperimentalSerializationApi::class)
fun deserializeEntityComponents(array: ByteArray): List<ComponentDto> {
    return COMPONENT_CBOR.decodeFromByteArray(ListSerializer(serializer<ComponentDto>()), array)
}

fun WriteComponentAccess.instantiateEntity(id: PersistentId, components: List<Component>): EntityId {
    return addEntity {
        setComponent(PersistentIdComponent(id))
        components.forEach { setComponent(it) }
    }
}

@Serializable
data class ComponentDto(val id: String, val data: ComponentData)

@Serializable
sealed interface ComponentData

@Serializable
data class CopyComponentDto(val component: Component) : ComponentData

// TODO: сериализовать с графом объектов
@Serializable
data class ScriptComponentDto(val value: ScriptValue) : ComponentData

@Serializable
data class ParentComponentDto(val isScript: Boolean, val parent: PersistentIdComponent) : ComponentData

@Serializable
data class ChildrenComponentDto(val isScript: Boolean, val children: Set<PersistentIdComponent>) : ComponentData

@Serializable
data class ContainedInDto(val container: PersistentId) : ComponentData

// на клиенте превращается в EntityRpcQueue
@Serializable
object EntityRpcReceiverDto : ComponentData

data class ComponentLoadSettings(
    val itemStorage: Storage<PersistentId, EngineItem>,
    val namespacedStorage: NamespacedStorageAccess,
    val persistentIdToEntity: MutableMap<PersistentId, EntityId>,
    val isClient: Boolean = false
)

context(world: World)
fun Component.toSnapshotDto(): ComponentDto {
    val type = when (this) {
        is ScriptComponent -> type.ecsType
        else -> componentTypeOf(this::class)
    }
    val data = when (this) {
        is ScriptComponent -> ScriptComponentDto(luaValue.toScriptValue())
        is Count -> CopyComponentDto(this.copy())
        is Entries -> CopyComponentDto(Entries(items.toMutableList()))
        is Flashlight -> CopyComponentDto(this.copy())
        is Gun -> CopyComponentDto(this.copy())
        is Luminance -> CopyComponentDto(this.copy())
        is OccupiedSlots -> CopyComponentDto(OccupiedSlots(slots.toMutableSet()))
        is Writable -> CopyComponentDto(this.copy())
        is Parent -> ParentComponentDto(false, entity.requireComponent())
        is Children -> ChildrenComponentDto(false, entities.map { it.requireComponent<PersistentIdComponent>() }.toSet())
        is EntityRpcReceiver -> EntityRpcReceiverDto
        else -> CopyComponentDto(this)
    }
    return ComponentDto(type.id, data)
}

fun ComponentDto.toDomainWithoutRelationships(itemStorage: Storage<PersistentId, EngineItem>, namespacedStorage: NamespacedStorageAccess): Component? {
    return toDomain(
        ComponentLoadSettings(itemStorage, namespacedStorage, mutableMapOf()),
        { error("This entity type not supports components with relationships") }
    )
}

fun ComponentDto.toDomain(
    settings: ComponentLoadSettings,
    entityGetter: (PersistentId) -> EntityId?,
    scriptComponentTypeNotFound: (ScriptComponentId, ScriptComponentDto) -> ScriptComponentType = { id, dto -> error("Invalid script component type $id") },
): Component? {
    return runBlocking {
        toDomainSuspend(settings, entityGetter, scriptComponentTypeNotFound)
    }
}

suspend fun ComponentDto.toDomainSuspend(
    settings: ComponentLoadSettings,
    entityGetter: suspend (PersistentId) -> EntityId?,
    scriptComponentTypeNotFound: (ScriptComponentId, ScriptComponentDto) -> ScriptComponentType = { id, dto -> error("Invalid script component type $id") },
): Component? {
    val notNullEntityGetter: suspend (PersistentIdComponent) -> EntityId = { settings.persistentIdToEntity[it.id] ?: entityGetter(it.id) ?: error("Entity with id $id not found") }
    val data = when (data) {
        is CopyComponentDto -> data.component
        is ScriptComponentDto -> {
            val componentId = ScriptComponentId(id)
            val scriptComponent = settings.namespacedStorage.components[componentId]
                ?: CoreScriptComponents.get(componentId)
                ?: scriptComponentTypeNotFound(componentId, data)
            val type = scriptComponent
            createLuaScriptComponent(data.value, type)
        }

        is ChildrenComponentDto -> when (data.isScript) {
            false -> Children(
                data.children.map { notNullEntityGetter(it) }.toMutableSet()
            )

            true -> ScriptComponent(
                data.children.map { notNullEntityGetter(it) }.toLuaArray { it.toLuaValue() },
                CoreScriptComponents.CHILDREN
            )
        }

        is ParentComponentDto -> when (data.isScript) {
            false -> Parent(notNullEntityGetter(data.parent))
            true -> ScriptComponent(
                data.parent.id.toString().toLuaValue(),
                CoreScriptComponents.PARENT
            )
        }

        // isClient означает, что мы получаем компонент по сети, а значит RpcReceiver нужно преобразовать в RpcQueue
        is EntityRpcReceiverDto -> when(settings.isClient) {
            true -> EntityRpcQueue(LinkedList())
            false -> EntityRpcReceiver(LinkedList())
        }

        is ContainedInDto -> null
    }
    return data
}

data class ComponentLoadException(val dto: ComponentDto, override val cause: Throwable) : RuntimeException("Cannot load component $dto")

private suspend fun ComponentDto.toDomainCatching(toDomainFunction: suspend ComponentDto.() -> Component?): Component? {
    return try {
        toDomainFunction(this)
    } catch (e: Exception) {
        throw ComponentLoadException(this, e)
    }
}

suspend fun List<ComponentDto>.toDomainSuspend(toDomainFunction: suspend ComponentDto.() -> Component?): List<Component> {
    return mapNotNull { componentDto -> componentDto.toDomainCatching(toDomainFunction) }
}

context(write: WriteComponentAccess)
suspend fun EntityId.copyComponentDtoState(
    components: List<ComponentDto>,
    transformer: suspend List<ComponentDto>.(suspend ComponentDto.() -> Component?) -> List<Component> = List<ComponentDto>::toDomainSuspend,
    toDomainFunction: ComponentDto.() -> Component?,
) {
    copyState(components.transformer(toDomainFunction))
}