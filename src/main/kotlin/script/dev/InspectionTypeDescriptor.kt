package org.lain.engine.script.dev

import kotlinx.serialization.Serializable
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
import org.lain.engine.script.dev.InspectionTypeDescriptor.Collection
import org.lain.engine.script.dev.InspectionTypeDescriptor.Enum
import org.lain.engine.script.dev.InspectionTypeDescriptor.Map
import org.lain.engine.script.dev.InspectionTypeDescriptor.Nullable
import org.lain.engine.script.dev.InspectionTypeDescriptor.Scalar
import org.lain.engine.script.dev.InspectionTypeDescriptor.Unknown
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.full.withNullability

enum class ScalarKind {
    STRING, CHAR, INT, BOOL, DOUBLE, UUID, ID, ENTITY_REF, INSTANT
}

@Serializable
sealed interface InspectionTypeDescriptor {
    @Serializable
    data class Scalar(
        val kind: ScalarKind,
    ) : InspectionTypeDescriptor

    @Serializable
    data class Record(
        val name: String,
        val fields: List<Field>,
    ) : InspectionTypeDescriptor {
        @Serializable
        data class Field(
            val name: String,
            val typeId: Int,
            val optional: Boolean,
        )
    }

    @Serializable
    data class Collection(
        val elementTypeId: Int,
    ) : InspectionTypeDescriptor

    @Serializable
    data class Map(
        val keyTypeId: Int,
        val valueTypeId: Int,
    ) : InspectionTypeDescriptor

    @Serializable
    data class Enum(
        val values: List<String>,
    ) : InspectionTypeDescriptor

    @Serializable
    data class Nullable(
        val valueTypeId: Int,
    ) : InspectionTypeDescriptor

    @Serializable
    data object Unknown : InspectionTypeDescriptor
}

context(ctx: InspectionSerializationContext)
fun KType.inspectionTypeDescriptor(): InspectionTypeDescriptor {
    require(!isMarkedNullable) {
        "Nullable types must be registered through InspectionSerializationContext.type: $this"
    }

    val typeClass = classifier as? KClass<*> ?: return Unknown

    return when {
        typeClass.isValue -> {
            val valueClassPropertyType = typeClass.valueClassProperty().returnType
            if (valueClassPropertyType.isMarkedNullable) {
                Nullable(ctx.type(valueClassPropertyType.withNullability(false)))
            } else {
                valueClassPropertyType.inspectionTypeDescriptor()
            }
        }

        typeClass.isSubclassOf(kotlin.collections.Map::class) -> {
            val (keyType, valueType) = mapTypeArgumentsOrNull() ?: (null to null)
            Map(
                keyTypeId = keyType?.let { ctx.type(it) } ?: ctx.type(Unknown),
                valueTypeId = valueType?.let { ctx.type(it) } ?: ctx.type(Unknown),
            )
        }

        typeClass.isSubclassOf(kotlin.collections.Collection::class) -> {
            val elementType = collectionTypeArgumentOrNull()
            Collection(
                elementTypeId = elementType?.let { ctx.type(it) } ?: ctx.type(Unknown),
            )
        }

        typeClass.java.isEnum -> Enum(
            typeClass.java.enumConstants.map { (it as kotlin.Enum<*>).name }
        )

        typeClass == String::class -> Scalar(ScalarKind.STRING)
        typeClass == Char::class -> Scalar(ScalarKind.CHAR)
        typeClass == Boolean::class -> Scalar(ScalarKind.BOOL)
        typeClass == Int::class -> Scalar(ScalarKind.INT)
        typeClass.isSubclassOf(Number::class) -> Scalar(ScalarKind.DOUBLE)
        typeClass == UUID::class || typeClass == EngineUuid::class -> Scalar(ScalarKind.UUID)
        typeClass == EngineId::class -> Scalar(ScalarKind.ID)
        typeClass == Instant::class -> Scalar(ScalarKind.INSTANT)
        typeClass == EnginePlayer::class || typeClass == PlayerPhysics::class -> Unknown

        else -> typeClass.recordDescriptor()
    }
}

context(ctx: InspectionSerializationContext)
private fun KClass<*>.recordDescriptor(): InspectionTypeDescriptor.Record {
    val parameters = primaryConstructor
        ?.parameters
        ?.associateBy { it.name }
        .orEmpty()

    return InspectionTypeDescriptor.Record(
        name = qualifiedName ?: simpleName ?: "<anonymous>",
        fields = constructorProperties().map { property ->
            InspectionTypeDescriptor.Record.Field(
                name = property.name,
                typeId = ctx.type(property.returnType),
                optional = parameters[property.name]?.isOptional == true,
            )
        },
    )
}

context(ctx: InspectionSerializationContext)
fun ScriptInspectionValue.inspectionTypeDescriptorId(): Int {
    return when (this) {
        is ScriptInspectionValue.Table -> {
            val unknownTypeId = ctx.type(Unknown)
            ctx.type(Map(unknownTypeId, unknownTypeId))
        }

        is ScriptInspectionValue.List -> ctx.type(
            Collection(ctx.type(Unknown))
        )

        is ScriptInspectionValue.Primitive -> ctx.type(
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

        is ScriptInspectionValue.Jvm -> ctx.type(value::class.starProjectedType)
        else -> ctx.type(Unknown)
    }
}
