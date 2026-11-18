package org.lain.engine.util.property

import org.lain.engine.data.persistentId
import org.lain.engine.script.SBool
import org.lain.engine.script.SEntityRef
import org.lain.engine.script.SId
import org.lain.engine.script.SInstant
import org.lain.engine.script.SInt
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.util.getDebugName
import org.lain.engine.util.getEntityDebugNameId
import org.lain.engine.util.property.PropertyScalar.Bool
import org.lain.engine.util.property.PropertyScalar.Double
import org.lain.engine.util.property.PropertyScalar.EntityRef
import org.lain.engine.util.property.PropertyScalar.Id
import org.lain.engine.util.property.PropertyScalar.Instant
import org.lain.engine.util.property.PropertyScalar.Int
import org.lain.engine.util.property.PropertyScalar.Str
import org.lain.engine.util.property.PropertyValue.Primitive
import kotlin.reflect.full.starProjectedType

context(ctx: PropertyTreeBuilder)
fun ScriptValuePrimitive.toScriptInspectionValue() = Primitive(
    when (this) {
        is SEntityRef -> id.serializeEntityRef()
        is SId -> Id(id)
        is SInstant -> Instant(instant.toEpochMilli())
        is SString -> Str(value)
        is SNumber -> Double(value)
        is SBool -> Bool(value)
        is SInt -> Int(value)
    }
)

context(ctx: PropertyTreeBuilder)
fun ScriptValueBinding.inspectScriptValue(
    location: PropertyLocation,
): Property {
    if (this is ScriptValueBinding.Jvm) {
        return value.jvmProperty(value::class.starProjectedType, location)
    }

    val typeDescriptorId = inspectionTypeDescriptorId()
    val inspectionValue = inspectScriptInspectionValue(location)
    return ctx.property(inspectionValue, location, typeDescriptorId)
}

context(ctx: PropertyTreeBuilder)
private fun ScriptValueBinding.inspectScriptInspectionValue(
    location: PropertyLocation,
): PropertyValue {
    return when (this) {
        is ScriptValueBinding.Primitive -> value.toScriptInspectionValue()
        is ScriptValueBinding.Table -> inspectScriptTable(location)
        is ScriptValueBinding.List -> inspectScriptList(location)
        is ScriptValueBinding.Jvm -> error("JVM values are handled by inspectScriptValue")
        else -> error("Unsupported script inspection value class: $this")
    }
}

context(ctx: PropertyTreeBuilder)
private fun ScriptValueBinding.Table.inspectScriptTable(
    location: PropertyLocation,
): PropertyValue.Reference = reference(identity = identity) {
    PropertyCompound.Table(
        entries.map { (scriptKey, scriptValue) ->
            val propertyKey = scriptKey.prepareScriptKey()
            val keyNode = scriptKey.inspectScriptValue(location.entryKey(scriptKey, propertyKey))
            val valueLocation = location.entryValue(scriptKey, propertyKey)
            PropertyCompound.Table.Entry(
                key = keyNode,
                value = scriptValue.inspectScriptValue(valueLocation),
            )
        }
    )
}

context(ctx: PropertyTreeBuilder)
private fun ScriptValueBinding.List.inspectScriptList(
    location: PropertyLocation,
): PropertyValue.Reference = reference(identity = identity) {
    PropertyCompound.Collection(
        values.mapIndexed { index, value ->
            value.inspectScriptValue(location.index(index))
        }
    )
}

context(ctx: PropertyTreeBuilder)
private fun ScriptValueBinding.prepareScriptKey(): PropertyValue? = when (this) {
    is ScriptValueBinding.Primitive -> value.toScriptInspectionValue()
    is ScriptValueBinding.Jvm -> prepareJvmKey(value, value::class.starProjectedType)
    is ScriptValueBinding.Table, is ScriptValueBinding.List -> ctx.reserveReference(identity)
    else -> error("Unsupported script inspection value class: $this")
}
