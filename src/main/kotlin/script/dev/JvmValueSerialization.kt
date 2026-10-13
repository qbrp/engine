package org.lain.engine.script.dev

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.script.EngineId
import org.lain.engine.script.dev.InspectionPrimitive.Bool
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.Id
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionPrimitive.Str
import org.lain.engine.script.dev.InspectionPrimitive.Other
import org.lain.engine.script.dev.InspectionPrimitive.Uuid
import org.lain.engine.script.dev.InspectionValue.Null
import org.lain.engine.script.dev.InspectionValue.Primitive
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

context(ctx: InspectionSerializationContext)
fun Any?.toJvmInspectionValue(jvmType: KType?, location: InspectionLocation?): InspectionValue {
    location?.live?.let { ctx.editTarget(it) }
    return when (this) {
        is String -> Primitive(Str(this))
        is kotlin.Int -> Primitive(Int(this))
        is kotlin.Double -> Primitive(Double(this))
        is Number -> Primitive(Double(this.toDouble()))
        is Boolean -> Primitive(Bool(this))
        is Char -> Primitive(InspectionPrimitive.Char(this))
        is UUID -> Primitive(Uuid(this.toString()))
        is kotlin.Enum<*> -> Primitive(Str(name))
        is EnginePlayer -> Primitive(Other(this.toString()))
        is EngineId -> Primitive(Id(this))
        null -> Null
        else -> {
            val clazz = this::class
            if (clazz.isValue) {
                @Suppress("UNCHECKED_CAST")
                val property = (clazz.memberProperties.first() as KProperty1<Any, *>)
                property.get(this).toJvmInspectionValue(jvmType, location)
            } else {
                this.toJvmInspectionObject(jvmType, location)
            }
        }
    }
}

context(ctx: InspectionSerializationContext)
fun Any.toJvmInspectionObject(jvmType: KType?, location: InspectionLocation?): InspectionValue.Reference =
    reference(location, jvmType, this) { targetId ->
        when (this) {
            is Collection<*> -> {
                val listType = jvmType?.listTypeArgument()
                InspectionObject.Collection(
                    this.mapIndexed { index, element ->
                        val elementLocation = location?.index(index)
                        element.toJvmInspectionValue(
                            listType,
                            elementLocation,
                        ).asNode(targetId, elementLocation)
                    }
                )
            }

            is Map<*, *> -> {
                val types = jvmType?.mapTypeArguments()
                InspectionObject.Table(
                    this.map { (key, value) ->
                        val keyI = key.toJvmInspectionValue(types?.first, null).asNode(null, null)
                        val valueLocation = location?.tableEntry(key, keyI.value)
                        InspectionObject.Table.Entry(
                            keyI,
                            value.toJvmInspectionValue(
                                types?.second,
                                valueLocation,
                            ).asNode(targetId, valueLocation)
                        )
                    }
                )
            }

            is PlayerPhysics -> InspectionObject.PlayerPhysics(noClip, collides.map { it.toString() })

            else -> {
                val properties = this::class.constructorProperties()
                InspectionObject.Record(
                    properties.associate { property ->
                        val propertyName = property.name
                        val propertyLocation = location?.field(propertyName)
                        propertyName to property.get(this).toJvmInspectionValue(
                            property.returnType,
                            propertyLocation,
                        ).asNode(targetId, propertyLocation)
                    }
                )
            }
        }
    }

@Suppress("UNCHECKED_CAST")
fun KClass<*>.constructorProperties(): List<KProperty1<Any, *>> {
    val constructorPropertyNames = primaryConstructor
        ?.parameters
        ?.mapNotNull { it.name }
        ?.toSet()
        .orEmpty()

    return memberProperties
        .filter { it.name in constructorPropertyNames } as List<KProperty1<Any, *>>
}
