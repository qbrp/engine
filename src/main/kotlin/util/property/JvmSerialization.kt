package org.lain.engine.util.property

import org.lain.cyberia.ecs.EntityId
import org.lain.engine.data.Uuid as EngineUuid
import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.script.EngineId
import org.lain.engine.util.collectionTypeArgumentOrNull
import org.lain.engine.util.constructorProperties
import org.lain.engine.util.mapTypeArgumentsOrNull
import org.lain.engine.util.property.PropertyScalar.Bool
import org.lain.engine.util.property.PropertyScalar.Char
import org.lain.engine.util.property.PropertyScalar.Double
import org.lain.engine.util.property.PropertyScalar.Float
import org.lain.engine.util.property.PropertyScalar.Id
import org.lain.engine.util.property.PropertyScalar.Instant
import org.lain.engine.util.property.PropertyScalar.Int
import org.lain.engine.util.property.PropertyScalar.Str
import org.lain.engine.util.property.PropertyScalar.Uuid
import org.lain.engine.util.property.PropertyValue.Primitive
import org.lain.engine.util.propertyClassifier
import org.lain.engine.util.valueClassProperty
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.starProjectedType

data class JvmProperty(
    val type: KType,
    val value: Any?,
    val location: PropertyLocation
) {
    val kClass: KClass<*>
        get() = type.propertyClassifier
}

context(ctx: PropertyTreeBuilder)
fun Any?.jvmProperty(
    type: KType,
    location: PropertyLocation,
): Property = JvmProperty(type, this, location).compoundSnapshot()

context(ctx: PropertyTreeBuilder)
fun JvmProperty.compoundSnapshot(): Property {
    val typeDescriptor = type.registerTypeDescriptor()
    return ctx.property(
        snapshotPropertyValue(),
        location,
        typeDescriptor,
    )
}

context(ctx: PropertyTreeBuilder)
private fun JvmProperty.snapshotPropertyValue(): PropertyValue? {
    val value = value ?: return null
    val typeClass = kClass

    if (typeClass.isValue) {
        val property = typeClass.valueClassProperty()
        return JvmProperty(property.returnType, property.get(value), location).snapshotPropertyValue()
    }

    snapshotScalar(typeClass, value)?.let { return it }
    return when (value) {
        is EnginePlayer -> value.reference { PropertyCompound.Player(value.toString()) }
        is PlayerPhysics -> value.compoundSnapshot()
        is Map<*, *> -> compoundSnapshot(value)
        is Collection<*> -> compoundSnapshot(value)
        else -> value.compoundSnapshot(location)
    }
}

context(builder: PropertyTreeBuilder)
private fun snapshotScalar(typeClass: KClass<*>, value: Any): PropertyValue? = when {
    typeClass == String::class -> Primitive(Str(value as String))
    typeClass == kotlin.Int::class -> Primitive(Int(value as kotlin.Int))
    typeClass == Boolean::class -> Primitive(Bool(value as Boolean))
    typeClass == kotlin.Char::class -> Primitive(Char(value as kotlin.Char))
    typeClass == kotlin.Double::class -> Primitive(Double(value as kotlin.Double))
    typeClass == kotlin.Float::class -> Primitive(Float(value as kotlin.Float))
    typeClass == UUID::class || typeClass == EngineUuid::class -> Primitive(Uuid(value.toString()))
    typeClass == EngineId::class -> Primitive(Id(value as EngineId))
    typeClass == java.time.Instant::class -> Primitive(Instant((value as java.time.Instant).toEpochMilli()))
    typeClass == EntityId::class -> Primitive((value as EntityId).serializeEntityRef())
    typeClass.java.isEnum -> Primitive(PropertyScalar.Enum((value as Enum<*>).name))
    else -> null
}

context(ctx: PropertyTreeBuilder)
internal fun prepareJvmKey(value: Any?, declaredType: KType?): PropertyValue? {
    if (value == null) return null
    val type = effectiveJvmType(declaredType, value) ?: error("Cannot resolve type of map key")
    val typeClass = type.propertyClassifier
    if (typeClass.isValue) {
        val property = typeClass.valueClassProperty()
        return prepareJvmKey(property.get(value), property.returnType)
    }
    return snapshotScalar(typeClass, value) ?: ctx.reserveReference(value)
}

context(ctx: PropertyTreeBuilder)
private fun PlayerPhysics.compoundSnapshot(): PropertyValue.Reference = reference {
    PropertyCompound.PlayerPhysics(
        noClip = noClip,
        collides = collides.map { it.toString() },
    )
}

context(ctx: PropertyTreeBuilder)
private fun JvmProperty.compoundSnapshot(collection: Collection<*>): PropertyValue.Reference = collection.reference {
    val elementType = type.collectionTypeArgumentOrNull()
    PropertyCompound.Collection(
        collection.mapIndexed { index, element ->
            element.inspectJvmChild(elementType, location.index(index))
        }
    )
}

context(ctx: PropertyTreeBuilder)
private fun JvmProperty.compoundSnapshot(map: Map<*, *>): PropertyValue.Reference = map.reference {
    val (keyType, valueType) = type.mapTypeArgumentsOrNull() ?: (null to null)
    PropertyCompound.Table(
        map.entries.map { (key, value) ->
            val propertyKey = prepareJvmKey(key, keyType)
            val keyNode = key.inspectJvmChild(keyType, location.entryKey(key, propertyKey))
            val valueLocation = location.entryValue(key, propertyKey)
            PropertyCompound.Table.Entry(
                key = keyNode,
                value = value.inspectJvmChild(valueType, valueLocation),
            )
        }
    )
}

context(ctx: PropertyTreeBuilder)
private fun Any.compoundSnapshot(location: PropertyLocation): PropertyValue.Reference = reference {
    PropertyCompound.Record(
        this::class.constructorProperties().associate { property ->
            val propertyLocation = location.field(property.name)
            property.name to property.get(this).inspectJvmChild(
                property.returnType,
                propertyLocation,
            )
        }
    )
}

context(ctx: PropertyTreeBuilder)
private fun Any?.inspectJvmChild(
    declaredType: KType?,
    location: PropertyLocation,
): Property {
    val effectiveType = effectiveJvmType(declaredType, this)
    return if (effectiveType != null) {
        this.jvmProperty(effectiveType, location)
    } else {
        ctx.property(null, location, ctx.unknownType())
    }
}

private fun effectiveJvmType(declaredType: KType?, value: Any?): KType? {
    val declaredClass = declaredType?.classifier as? KClass<*>
    return if (declaredClass != null && (declaredClass != Any::class || value == null)) {
        declaredType
    } else {
        value?.let { it::class.starProjectedType }
    }
}
