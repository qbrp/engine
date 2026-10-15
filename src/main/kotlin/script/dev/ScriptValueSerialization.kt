package org.lain.engine.script.dev

import org.lain.engine.script.SBool
import org.lain.engine.script.SEntityRef
import org.lain.engine.script.SId
import org.lain.engine.script.SInstant
import org.lain.engine.script.SInt
import org.lain.engine.script.SNumber
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.script.dev.InspectionPrimitive.Bool
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.EntityRef
import org.lain.engine.script.dev.InspectionPrimitive.Id
import org.lain.engine.script.dev.InspectionPrimitive.Instant
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionPrimitive.Str
import org.lain.engine.script.dev.InspectionValue.Primitive
import kotlin.reflect.full.starProjectedType

fun ScriptValuePrimitive.toScriptInspectionValue() = Primitive(
    when (this) {
        is SEntityRef -> EntityRef(id)
        is SId -> Id(id)
        is SInstant -> Instant(instant.toEpochMilli())
        is SString -> Str(value)
        is SNumber -> Double(value)
        is SBool -> Bool(value)
        is SInt -> Int(value)
    }
)

context(ctx: InspectionSerializationContext)
fun ScriptInspectionValue.inspectScriptValue(
    location: InspectionLocation?,
): InspectionNode {
    if (this is ScriptInspectionValue.Jvm) {
        return JvmTypedValue(value::class.starProjectedType, value)
            .inspectJvmValue(location)
    }

    val typeDescriptorId = inspectionTypeDescriptorId()
    val inspectionValue = inspectScriptInspectionValue(location)
    return inspectionValue.asNode(location, typeDescriptorId)
}

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.inspectScriptInspectionValue(
    location: InspectionLocation?,
): InspectionValue {
    return when (this) {
        is ScriptInspectionValue.Primitive -> value.toScriptInspectionValue()
        is ScriptInspectionValue.Table -> inspectScriptTable(location)
        is ScriptInspectionValue.List -> inspectScriptList(location)
        is ScriptInspectionValue.Jvm -> error("JVM values are handled by inspectScriptValue")
        else -> error("Unsupported script inspection value class: $this")
    }
}

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.Table.inspectScriptTable(
    location: InspectionLocation?,
): InspectionValue.Reference = reference(identity = identity) {
    InspectionObject.Table(
        entries.map { (scriptKey, scriptValue) ->
            val keyNode = scriptKey.inspectScriptValue(null)
            val valueLocation = location?.tableEntry(scriptKey, keyNode.value)
            InspectionObject.Table.Entry(
                key = keyNode,
                value = scriptValue.inspectScriptValue(valueLocation),
            )
        }
    )
}

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.List.inspectScriptList(
    location: InspectionLocation?,
): InspectionValue.Reference = reference(identity = identity) {
    InspectionObject.Collection(
        values.mapIndexed { index, value ->
            value.inspectScriptValue(location?.index(index))
        }
    )
}
