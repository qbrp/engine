package org.lain.engine.script.dev

import org.lain.engine.data.Uuid as EngineUuid
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.script.EngineId
import org.lain.engine.script.dev.InspectionPrimitive.Bool
import org.lain.engine.script.dev.InspectionPrimitive.Char
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.Id
import org.lain.engine.script.dev.InspectionPrimitive.Instant
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionPrimitive.Other
import org.lain.engine.script.dev.InspectionPrimitive.Str
import org.lain.engine.script.dev.InspectionPrimitive.Uuid
import org.lain.engine.script.dev.InspectionValue.Null
import org.lain.engine.script.dev.InspectionValue.Primitive
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

data class JvmTypedValue(
    val type: KType,
    val value: Any?,
) {
    val kClass: KClass<*>
        get() = type.propertyClassifier
}

context(ctx: InspectionSerializationContext)
fun Any?.inspectJvmValue(
    type: KType,
    location: InspectionLocation?,
): InspectionNode = JvmTypedValue(type, this).inspectJvmValue(location)

context(ctx: InspectionSerializationContext)
fun JvmTypedValue.inspectJvmValue(
    location: InspectionLocation?,
): InspectionNode {
    val typeDescriptorId = ctx.type(type)
    val inspectionValue = inspectJvmInspectionValue(location)
    return inspectionValue.asNode(location, typeDescriptorId)
}

context(ctx: InspectionSerializationContext)
private fun JvmTypedValue.inspectJvmInspectionValue(
    location: InspectionLocation?,
): InspectionValue {
    val value = value ?: return Null
    val typeClass = kClass

    return when {
        typeClass.isValue -> {
            val property = typeClass.valueClassProperty()
            JvmTypedValue(property.returnType, property.get(value))
                .inspectJvmInspectionValue(location)
        }

        typeClass == String::class -> Primitive(Str(value as String))
        typeClass == Int::class -> Primitive(Int(value as kotlin.Int))
        typeClass == Boolean::class -> Primitive(Bool(value as Boolean))
        typeClass == Char::class -> Primitive(Char(value as kotlin.Char))
        typeClass.isSubclassOf(Number::class) -> Primitive(Double((value as Number).toDouble()))
        typeClass == UUID::class || typeClass == EngineUuid::class -> Primitive(Uuid(value.toString()))
        typeClass == EngineId::class -> Primitive(Id(value as EngineId))
        typeClass == java.time.Instant::class -> Primitive(
            Instant((value as java.time.Instant).toEpochMilli())
        )

        typeClass.java.isEnum -> Primitive(
            InspectionPrimitive.Enum((value as kotlin.Enum<*>).name)
        )

        typeClass == EnginePlayer::class -> Primitive(Other(value.toString()))
        typeClass == PlayerPhysics::class -> (value as PlayerPhysics).inspectPlayerPhysics()
        typeClass.isSubclassOf(Map::class) -> (value as Map<*, *>).inspectJvmMap(type, location)
        typeClass.isSubclassOf(Collection::class) ->
            (value as Collection<*>).inspectJvmCollection(type, location)

        else -> value.inspectJvmRecord(location)
    }
}

context(ctx: InspectionSerializationContext)
private fun PlayerPhysics.inspectPlayerPhysics(): InspectionValue.Reference = reference {
    InspectionObject.PlayerPhysics(
        noClip = noClip,
        collides = collides.map { it.toString() },
    )
}

context(ctx: InspectionSerializationContext)
private fun Collection<*>.inspectJvmCollection(
    declaredType: KType,
    location: InspectionLocation?,
): InspectionValue.Reference = reference {
    val elementType = declaredType.collectionTypeArgumentOrNull()
    InspectionObject.Collection(
        mapIndexed { index, element ->
            element.inspectJvmChild(elementType, location?.index(index))
        }
    )
}

context(ctx: InspectionSerializationContext)
private fun Map<*, *>.inspectJvmMap(
    declaredType: KType,
    location: InspectionLocation?,
): InspectionValue.Reference = reference {
    val (keyType, valueType) = declaredType.mapTypeArgumentsOrNull() ?: (null to null)
    InspectionObject.Table(
        entries.map { (key, value) ->
            val keyNode = key.inspectJvmChild(keyType, null)
            val valueLocation = location?.tableEntry(key, keyNode.value)
            InspectionObject.Table.Entry(
                key = keyNode,
                value = value.inspectJvmChild(valueType, valueLocation),
            )
        }
    )
}

context(ctx: InspectionSerializationContext)
private fun Any.inspectJvmRecord(
    location: InspectionLocation?,
): InspectionValue.Reference = reference {
    InspectionObject.Record(
        this::class.constructorProperties().associate { property ->
            val propertyLocation = location?.field(property.name)
            property.name to property.get(this).inspectJvmChild(
                property.returnType,
                propertyLocation,
            )
        }
    )
}

context(ctx: InspectionSerializationContext)
private fun Any?.inspectJvmChild(
    declaredType: KType?,
    location: InspectionLocation?,
): InspectionNode {
    val concreteDeclaredType = declaredType?.takeIf { it.classifier is KClass<*> }
    val effectiveType = concreteDeclaredType ?: this?.let { it::class.starProjectedType }
    return if (effectiveType != null) {
        JvmTypedValue(effectiveType, this).inspectJvmValue(location)
    } else {
        Null.asNode(location, ctx.type(InspectionTypeDescriptor.Unknown))
    }
}
