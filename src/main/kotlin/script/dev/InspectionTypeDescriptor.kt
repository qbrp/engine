package org.lain.engine.script.dev

import kotlinx.serialization.Serializable
import org.lain.engine.script.EngineId
import org.lain.engine.script.SBool
import org.lain.engine.script.SEntityRef
import org.lain.engine.script.SId
import org.lain.engine.script.SInstant
import org.lain.engine.script.SInt
import org.lain.engine.script.SJvm
import org.lain.engine.script.SList
import org.lain.engine.script.SNil
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.STable
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.script.dev.InspectionTypeDescriptor.*
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSubtypeOf
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
    if (isMarkedNullable) {
        val innerType = withNullability(false)
        return InspectionTypeDescriptor.Nullable(
            valueTypeId = ctx.type(innerType),
        )
    }

    val classifier = classifier as? KClass<*> ?: error("Classifier not found")

    return when {
        classifier.isSubclassOf(List::class) -> {
            InspectionTypeDescriptor.Collection(
                ctx.type(listTypeArgument())
            )
        }

        classifier.isSubclassOf(Map::class) -> {
            val (keyType, valueType) = mapTypeArguments()
            InspectionTypeDescriptor.Map(
                ctx.type(keyType),
                ctx.type(valueType),
            )
        }

        classifier.java.isEnum -> {
            InspectionTypeDescriptor.Enum(
                classifier.java.enumConstants
                    .map { (it as kotlin.Enum<*>).name }
            )
        }

        classifier == Int::class -> InspectionTypeDescriptor.Scalar(ScalarKind.INT)
        classifier == String::class -> InspectionTypeDescriptor.Scalar(ScalarKind.STRING)
        classifier == Char::class -> InspectionTypeDescriptor.Scalar(ScalarKind.CHAR)
        classifier == Boolean::class -> InspectionTypeDescriptor.Scalar(ScalarKind.BOOL)
        classifier == Double::class -> InspectionTypeDescriptor.Scalar(ScalarKind.DOUBLE)
        classifier == UUID::class -> InspectionTypeDescriptor.Scalar(ScalarKind.UUID)
        classifier == EngineId::class -> InspectionTypeDescriptor.Scalar(ScalarKind.ID)
        classifier == Instant::class -> InspectionTypeDescriptor.Scalar(ScalarKind.INSTANT)

        else -> classifier.recordDescriptor()
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
fun ScriptInspectionValue.inspectionTypeDescriptor() {
    when(this) {
        is ScriptInspectionValue.Table -> Map(
            ctx.type(Unknown),
            ctx.type(Unknown)
        )
        is ScriptInspectionValue.List -> Collection(
            ctx.type(Unknown)
        )
        is ScriptInspectionValue.Primitive -> when (this.value) {
            is SBool -> ctx.type(Scalar(ScalarKind.BOOL))
            is SEntityRef -> ctx.type(Scalar(ScalarKind.ENTITY_REF))
            is SId -> ctx.type(Scalar(ScalarKind.ID))
            is SInstant -> ctx.type(Scalar(ScalarKind.INSTANT))
            is SInt -> ctx.type(Scalar(ScalarKind.INT))
            is SNumber -> ctx.type(Scalar(ScalarKind.DOUBLE))
            is SString -> ctx.type(Scalar(ScalarKind.STRING))
        }
        is ScriptInspectionValue.Jvm -> ctx.type(value::class.starProjectedType)
        else -> ctx.type(Unknown)
    }
}