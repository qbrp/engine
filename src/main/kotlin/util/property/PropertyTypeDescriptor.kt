package org.lain.engine.util.property

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.data.Uuid as EngineUuid
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.script.EngineId
import org.lain.engine.script.SBool
import org.lain.engine.script.SEntityRef
import org.lain.engine.script.SId
import org.lain.engine.script.SInstant
import org.lain.engine.script.SInt
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.util.collectionTypeArgumentOrNull
import org.lain.engine.util.constructorProperties
import org.lain.engine.util.mapTypeArgumentsOrNull
import org.lain.engine.util.valueClassProperty
import org.lain.engine.util.property.PropertyTypeDescriptor.Collection
import org.lain.engine.util.property.PropertyTypeDescriptor.Enum
import org.lain.engine.util.property.PropertyTypeDescriptor.Map
import org.lain.engine.util.property.PropertyTypeDescriptor.Nullable
import org.lain.engine.util.property.PropertyTypeDescriptor.Scalar
import org.lain.engine.util.property.PropertyTypeDescriptor.Unknown
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

enum class ScalarKind {
    STRING, CHAR, INT, BOOL, DOUBLE, FLOAT, UUID, ID, ENTITY_REF, INSTANT
}

@Serializable
@JvmInline
value class PropertyTypeDescriptorId(val value: Int)

@Serializable
sealed interface PropertyTypeDescriptor {
    @Serializable
    data class Scalar(
        val kind: ScalarKind,
    ) : PropertyTypeDescriptor

    @Serializable
    data class Record(
        val name: String,
        val fields: List<Field>,
    ) : PropertyTypeDescriptor {
        @Serializable
        data class Field(
            val name: String,
            val type: PropertyTypeDescriptorId,
            val optional: Boolean,
        )
    }

    @Serializable
    data class Collection(
        val elementType: PropertyTypeDescriptorId,
    ) : PropertyTypeDescriptor

    @Serializable
    data class Map(
        val keyType: PropertyTypeDescriptorId,
        val valueType: PropertyTypeDescriptorId,
    ) : PropertyTypeDescriptor

    @Serializable
    data class Enum(
        val name: String,
        val values: List<String>,
    ) : PropertyTypeDescriptor

    @Serializable
    data class Nullable(
        val valueTypeId: PropertyTypeDescriptorId,
    ) : PropertyTypeDescriptor

    @Serializable
    data object Unknown : PropertyTypeDescriptor
}

context(ctx: PropertyTreeBuilder)
fun KType.registerTypeDescriptor(): PropertyTypeDescriptorId {
    if (isMarkedNullable) {
        return ctx.type(this) { Nullable(withNullability(false).registerTypeDescriptor()) }
    }

    val typeClass = classifier as? KClass<*> ?: return ctx.unknownType()

    return when {
        typeClass.isValue -> {
            val valueClassPropertyType = typeClass.valueClassProperty().returnType
            valueClassPropertyType.registerTypeDescriptor()
        }

        typeClass.isSubclassOf(kotlin.collections.Map::class) -> {
            val (keyType, valueType) = mapTypeArgumentsOrNull() ?: (null to null)
            ctx.type(this) {
                Map(
                    keyType = keyType?.registerTypeDescriptor() ?: ctx.unknownType(),
                    valueType = valueType?.registerTypeDescriptor() ?: ctx.unknownType(),
                )
            }
        }

        typeClass.isSubclassOf(kotlin.collections.Collection::class) -> {
            val elementType = collectionTypeArgumentOrNull()
            ctx.type(this) {
                Collection(
                    elementType = elementType?.registerTypeDescriptor() ?: ctx.unknownType(),
                )
            }
        }

        typeClass.java.isEnum ->
            ctx.type(this) {
                Enum(
                    typeClass.qualifiedName ?: typeClass.simpleName ?: "<anonymous>",
                    typeClass.java.enumConstants.map { (it as kotlin.Enum<*>).name }
                )
            }

        typeClass == String::class -> ctx.type(this) { Scalar(ScalarKind.STRING) }
        typeClass == Char::class -> ctx.type(this) { Scalar(ScalarKind.CHAR) }
        typeClass == Boolean::class -> ctx.type(this) { Scalar(ScalarKind.BOOL) }
        typeClass == Int::class -> ctx.type(this) { Scalar(ScalarKind.INT) }
        typeClass == Double::class -> ctx.type(this) { Scalar(ScalarKind.DOUBLE) }
        typeClass == Float::class -> ctx.type(this) { Scalar(ScalarKind.FLOAT) }
        typeClass == UUID::class || typeClass == EngineUuid::class -> ctx.type(this) { Scalar(ScalarKind.UUID) }
        typeClass == EngineId::class -> ctx.type(this) { Scalar(ScalarKind.ID) }
        typeClass == EntityId::class -> ctx.type(this) { Scalar(ScalarKind.ENTITY_REF) }
        typeClass == Instant::class -> ctx.type(this) { Scalar(ScalarKind.INSTANT) }
        typeClass == EnginePlayer::class || typeClass == PlayerPhysics::class -> ctx.unknownType()

        else -> typeClass.recordDescriptor(this)
    }
}

context(ctx: PropertyTreeBuilder)
private fun KClass<*>.recordDescriptor(type: KType): PropertyTypeDescriptorId {
    val parameters = primaryConstructor
        ?.parameters
        ?.associateBy { it.name }
        .orEmpty()

    return ctx.type(type) {
        PropertyTypeDescriptor.Record(
            name = qualifiedName ?: simpleName ?: "<anonymous>",
            fields = constructorProperties().map { property ->
                val propertyType = property.returnType
                PropertyTypeDescriptor.Record.Field(
                    name = property.name,
                    type = propertyType.registerTypeDescriptor(),
                    optional = parameters[property.name]?.isOptional == true,
                )
            },
        )
    }
}

context(ctx: PropertyTreeBuilder)
fun ScriptValueBinding.inspectionTypeDescriptorId(): PropertyTypeDescriptorId {
    return when (this) {
        is ScriptValueBinding.Table -> {
            val unknownTypeId = ctx.unknownType()
            ctx.type(Map(unknownTypeId, unknownTypeId))
        }

        is ScriptValueBinding.List -> ctx.type(
            Collection(ctx.unknownType())
        )

        is ScriptValueBinding.Primitive -> ctx.type(
            Scalar(
                when (value) {
                    is SBool -> ScalarKind.BOOL
                    is SEntityRef -> ScalarKind.ENTITY_REF
                    is SId -> ScalarKind.ID
                    is SInstant -> ScalarKind.INSTANT
                    is SInt -> ScalarKind.INT
                    is SNumber -> ScalarKind.DOUBLE
                    is SString -> ScalarKind.STRING
                }
            )
        )

        is ScriptValueBinding.Jvm -> value::class.starProjectedType.registerTypeDescriptor()
        else -> ctx.unknownType()
    }
}
