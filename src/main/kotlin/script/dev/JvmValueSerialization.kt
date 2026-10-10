package org.lain.engine.script.dev

import org.lain.engine.player.EnginePlayer
import org.lain.engine.player.PlayerPhysics
import org.lain.engine.script.EngineId
import org.lain.engine.script.dev.InspectionPrimitive.Bool
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.Enum
import org.lain.engine.script.dev.InspectionPrimitive.Id
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionPrimitive.Str
import org.lain.engine.script.dev.InspectionPrimitive.Other
import org.lain.engine.script.dev.InspectionPrimitive.Uuid
import org.lain.engine.script.dev.InspectionValue.Null
import org.lain.engine.script.dev.InspectionValue.Primitive
import org.lain.engine.script.dev.InspectionValue.Reference
import java.util.UUID
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

context(ctx: InspectionSerializationContext)
fun Any?.toJvmInspectionValue(parent: InspectedObject.Parent, readonly: Boolean): InspectionValue {
    return when (this) {
        is String -> Primitive(readonly, Str(this))
        is kotlin.Int -> Primitive(readonly, Int(this))
        is kotlin.Double -> Primitive(readonly, Double(this))
        is Number -> Primitive(readonly, Double(this.toDouble()))
        is Boolean -> Primitive(readonly, Bool(this))
        is Char -> Primitive(readonly, InspectionPrimitive.Char(this))
        is UUID -> Primitive(readonly, Uuid(this.toString()))
        is kotlin.Enum<*> -> Primitive(readonly, Enum(this::class.qualifiedName!!, name))
        is EnginePlayer -> Primitive(readonly, Other(this.toString()))
        is EngineId -> Primitive(readonly, Id(this))
        null -> Null
        else -> {
            val clazz = this::class
            when (clazz.isValue) {
                false -> Reference(appendSerializationContext(parent) { valueClass, id ->
                    valueClass.toJvmInspectionObject(id)
                })

                true -> {
                    val property = (clazz.memberProperties.first() as KProperty1<Any, *>)
                    property.get(this).toJvmInspectionValue(parent, readonly)
                }
            }
        }
    }
}

context(ctx: InspectionSerializationContext)
fun Any.toJvmInspectionObject(id: kotlin.Int): InspectionObject {
    return when (this) {
        is Collection<*> -> InspectionObject.Collection(
            this.mapIndexed { index, element ->
                element.toJvmInspectionValue(
                    InspectedObject.Parent.Index(id, index),
                    false
                )
            }
        )

        is Map<*, *> ->
            InspectionObject.Table(
                this.map { (k, v) ->
                    val property = k.toString()
                    property to v.toJvmInspectionValue(
                        InspectedObject.Parent.Property(id, property),
                        false
                    )
                }.toMap()
            )

        is PlayerPhysics -> InspectionObject.PlayerPhysics(noClip, collides.map { it.toString() })

        else -> {
            val constructorPropertyNames = this::class.primaryConstructor
                ?.parameters
                ?.mapNotNull { it.name }
                ?.toSet()
                .orEmpty()

            val properties = this::class.memberProperties
                .filter { it.name in constructorPropertyNames }

            InspectionObject.Table(
                properties.associate { property ->
                    val readonly = property !is KMutableProperty1<*, *>
                    val propertyName = property.name
                    property as KProperty1<Any, *>
                    propertyName to property.get(this).toJvmInspectionValue(
                        InspectedObject.Parent.Property(id, propertyName),
                        readonly
                    )
                }
            )
        }
    }
}
