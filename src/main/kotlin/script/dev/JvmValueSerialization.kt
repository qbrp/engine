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
import org.lain.engine.script.dev.InspectionPrimitive.Uneditable
import org.lain.engine.script.dev.InspectionPrimitive.Uuid
import org.lain.engine.script.dev.InspectionValue.Null
import org.lain.engine.script.dev.InspectionValue.Primitive
import org.lain.engine.script.dev.InspectionValue.Reference
import java.util.UUID
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

context(ctx: InspectionSerializationContext)
fun Any?.toJvmInspectionValue(readonly: Boolean): InspectionValue {
    return when (this) {
        is String -> Primitive(readonly, Str(this))
        is kotlin.Int -> Primitive(readonly, Int(this))
        is kotlin.Double -> Primitive(readonly, Double(this))
        is Number -> Primitive(readonly, Double(this.toDouble()))
        is Boolean -> Primitive(readonly, Bool(this))
        is Char -> Primitive(readonly, InspectionPrimitive.Char(this))
        is UUID -> Primitive(true, Uuid(this.toString()))
        is kotlin.Enum<*> -> Primitive(readonly, Enum(this::class.qualifiedName!!, name))
        is EnginePlayer -> Primitive(true, Uneditable(this.toString()))
        is EngineId -> Primitive(readonly, Id(this))
        null -> Null
        else -> {
            val clazz = this::class
            when (clazz.isValue) {
                false -> Reference(appendSerializationContext { it.toJvmInspectionObject() })
                true -> {
                    val property = (clazz.memberProperties.first() as KProperty1<Any, *>)
                    property.get(this).toJvmInspectionValue(property is KMutableProperty1<*, *>)
                }
            }
        }
    }
}

context(ctx: InspectionSerializationContext)
fun Any.toJvmInspectionObject(): InspectionObject {
    return when (this) {
        is Collection<*> -> InspectionObject.Collection(this.map { it.toJvmInspectionValue(true) })
        is Map<*, *> ->
            InspectionObject.Table(
                this.map { (k, v) ->
                    k.toString() to v.toJvmInspectionValue(true)
                }.toMap()
            )
        is PlayerPhysics -> InspectionObject.PlayerPhysics(noClip, collides.map { it.toString() })

        else -> {
            val properties = this::class.memberProperties
            InspectionObject.Table(
                properties.associate { prop ->
                    (prop.name to (prop as KProperty1<Any, *>).get(this).toJvmInspectionValue(true))
                }
            )
        }
    }
}