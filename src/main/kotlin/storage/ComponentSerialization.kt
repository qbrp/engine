package org.lain.engine.storage

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.polymorphic
import org.lain.cyberia.ecs.*
import org.lain.engine.container.ContainedIn
import org.lain.engine.container.Entries
import org.lain.engine.container.OccupiedSlots
import org.lain.engine.item.Count
import org.lain.engine.item.Flashlight
import org.lain.engine.item.Gun
import org.lain.engine.item.Writable
import org.lain.engine.script.CoreScriptComponents
import org.lain.engine.script.NamespacedStorageAccess
import org.lain.engine.script.ScriptComponent
import org.lain.engine.script.ScriptComponentId
import org.lain.engine.script.lua.luaValue
import org.lain.engine.script.lua.toJsonDeep
import org.lain.engine.script.lua.toLuaValue
import org.lain.engine.util.component.ComponentTypeRegistry
import org.lain.engine.world.Luminance
import org.lain.engine.world.World
import java.util.*
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
        subclass(ContainedInDto::class, ContainedInDto.serializer())
        subclass(ScriptComponentDto::class, ScriptComponentDto.serializer())
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

@Serializable
data class PersistentId(val value: String) : Component {
    override fun toString(): String = value
    companion object {
        fun next() = PersistentId(UUID.randomUUID().toString())
    }
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
        setComponent(id)
        components.forEach { setComponent(it) }
    }
}

@Serializable
data class ComponentDto(val id: String, val data: ComponentData)

interface ComponentData

@Serializable
data class CopyComponentDto(val component: Component) : ComponentData

@Serializable
data class ContainedInDto(val container: PersistentId) : ComponentData

@Serializable
data class ScriptComponentDto(val jsonString: String) : ComponentData

fun ScriptComponentDto(json: JsonElement): ScriptComponentDto {
    return ScriptComponentDto(Json.encodeToString(json))
}

data class ComponentLoadSettings(
    val referencedEntities: Map<PersistentId, EntityId>?,
    val namespacedStorage: NamespacedStorageAccess
)

context(world: World)
fun Component.toSnapshotDto(): ComponentDto {
    val data = when (this) {
        is ContainedIn -> ContainedInDto(container.requireComponent())
        is ScriptComponent -> { ScriptComponentDto(luaValue.toJsonDeep()) }
        is Count -> CopyComponentDto(this.copy())
        is Entries -> CopyComponentDto(Entries(items.toMutableList()))
        is Flashlight -> CopyComponentDto(this.copy())
        is Gun -> CopyComponentDto(this.copy())
        is Luminance -> CopyComponentDto(this.copy())
        is OccupiedSlots -> CopyComponentDto(OccupiedSlots(slots.toMutableSet()))
        is Writable -> CopyComponentDto(this.copy())
        else -> CopyComponentDto(this)
    }
    val type = when (this) {
        is ScriptComponent -> type.ecsType
        else -> componentTypeOf(this::class)
    }
    return ComponentDto(type.id, data)
}

/**
 * @param entities таблица сущносетй для решения, кому принадлежит контейнер. Если null, компонент будет игнорироваться
 */
fun ComponentDto.toDomain(settings: ComponentLoadSettings): Component? {
    val data = when (data) {
        is CopyComponentDto -> data.component
        is ContainedInDto -> {
            val container = data.container
            settings.referencedEntities?.let { it[container]
                ?: error("Container $container not found") }
                ?.let { ContainedIn(it) }
        }
        is ScriptComponentDto -> {
            val componentId = ScriptComponentId(id)
            val scriptComponent = settings.namespacedStorage.components[componentId]
                ?: CoreScriptComponents.get(componentId)
                ?: error("Component type $componentId is not registered")
            val type = scriptComponent
            ScriptComponent(Json.decodeFromString<JsonElement>(data.jsonString).toLuaValue(), type)
        }
        else -> error("Component ${this::class.simpleName} doesn't contains DTO mapper")
    }
    return data
}

context(write: WriteComponentAccess)
fun EntityId.copyComponentDtoState(settings: ComponentLoadSettings, components: List<ComponentDto>) {
    copyState(
        components
            .mapNotNull {
                val result = it.toDomain(settings)
                if (result == null) {
                    LOGGER.error("Не удалось загрузить компонент $it для сущности $this")
                }
                result
            }
    )
}