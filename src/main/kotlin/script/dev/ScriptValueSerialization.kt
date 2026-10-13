package org.lain.engine.script.dev

import org.lain.engine.script.*
import org.lain.engine.script.dev.InspectionPrimitive.*
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionValue.*
import kotlin.reflect.full.starProjectedType

fun ScriptValuePrimitive.toScriptInspectionValue() =
    Primitive(
        when (val primitive = this) {
            is SEntityRef -> EntityRef(primitive.id)
            is SId -> Id(primitive.id)
            is SInstant -> Instant(primitive.instant.toEpochMilli())
            is SString -> Str(primitive.value)
            is SNumber -> Double(primitive.value)
            is SBool -> Bool(primitive.value)
            is SInt -> Int(primitive.value)
        }
    )

context(ctx: InspectionSerializationContext)
fun ScriptInspectionValue.toScriptInspectionNode(
    targetId: kotlin.Int?,
    location: InspectionLocation?,
): InspectionNode {
    location?.live?.let { ctx.editTarget(it) }
    inspectionTypeDescriptor()
    return when (this) {
        is ScriptInspectionValue.Primitive -> value.toScriptInspectionValue().asNode(targetId, location)

        is ScriptInspectionValue.Table -> {
            toScriptInspectionObject(location).asNode(targetId, location)
        }

        is ScriptInspectionValue.List -> {
            toScriptInspectionObject(location).asNode(targetId, location)
        }

        is ScriptInspectionValue.Jvm -> {
            this.toJvmInspectionObject(value::class.starProjectedType, location).asNode(targetId, location)
        }

        else -> error("Unsupported script inspection value class: $this")
    }
}

context (ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.Table.toScriptInspectionObject(
    location: InspectionLocation?
) = reference(location, null, identity) { targetId ->
    InspectionObject.Table(
        entries.map { (keyL, valueL) ->
            val key = keyL.toScriptInspectionNode(
                targetId,
                null
            )
            val valueLocation = location?.tableEntry(keyL, key.value)
            val value = valueL.toScriptInspectionNode(
                targetId,
                valueLocation
            )
            InspectionObject.Table.Entry(key, value)
        }
    )
}

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.List.toScriptInspectionObject(location: InspectionLocation?) =
    reference(location, null, identity) { targetId ->
        InspectionObject.Collection(
            values.mapIndexed { index, value ->
                value.toScriptInspectionNode(
                    targetId,
                    location?.index(index)
                )
            }
        )
    }
