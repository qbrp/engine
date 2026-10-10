package org.lain.engine.script.dev

import org.lain.engine.player.gradientText
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
import org.lain.engine.script.dev.InspectionPrimitive.Bool
import org.lain.engine.script.dev.InspectionPrimitive.Double
import org.lain.engine.script.dev.InspectionPrimitive.Id
import org.lain.engine.script.dev.InspectionPrimitive.Int
import org.lain.engine.script.dev.InspectionPrimitive.Str
import org.lain.engine.script.dev.InspectionValue.Null
import org.lain.engine.script.dev.InspectionValue.Primitive
import org.lain.engine.script.dev.InspectionValue.Reference
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

context(ctx: InspectionSerializationContext)
fun ScriptInspectionValue.toScriptInspectionValue(
    parent: InspectedObject.Parent,
): InspectionValue {
    return when (this) {
        is ScriptInspectionValue.Primitive -> {
            when (val primitive = value) {
                is SEntityRef -> Primitive(false, Int(primitive.id))
                is SId -> Primitive(false, Id(primitive.id))
                is SInstant -> Primitive(
                    false, Str(
                        LocalDateTime.ofInstant(primitive.instant, ZoneId.systemDefault()).format(
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        )
                    )
                )

                is SString -> Primitive(false, Str(primitive.value))
                is SNumber -> Primitive(false, Double(primitive.value))
                is SBool -> Primitive(false, Bool(primitive.value))
                is SInt -> Primitive(false, Int(primitive.value))
                SNil -> Null
            }
        }

        is ScriptInspectionValue.Table -> {
            Reference(appendSerializationContext(parent, identity) { element, id ->
                element.toScriptInspectionObject(id)
            })
        }

        is ScriptInspectionValue.List -> {
            Reference(
                appendSerializationContext(parent, identity) { element, id ->
                    element.toScriptInspectionObject(id)
                }
            )
        }

        is ScriptInspectionValue.Jvm -> {
            Reference(
                appendSerializationContext(parent, identity) { jvmElement, id ->
                    jvmElement.toJvmInspectionObject(id)
                }
            )
        }

        else -> error("Unsupported script inspection value class: $this")
    }
}

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.Table.toScriptInspectionObject(id: kotlin.Int) = InspectionObject.Table(
    value.values.keys.associate { key ->
        val property = key.toInspectionKey()
        val childValue = child(key).toScriptInspectionValue(
            InspectedObject.Parent.Property(id, property),
        )
        property to childValue
    }
)

context(ctx: InspectionSerializationContext)
private fun ScriptInspectionValue.List.toScriptInspectionObject(id: kotlin.Int) = InspectionObject.Collection(
    value.values.mapIndexed { idx, _ ->
        child(idx + 1).toScriptInspectionValue(
            InspectedObject.Parent.Index(id, idx + 1),
        )
    }
)

fun ScriptValue.toInspectionKey(): String = when (this) {
    SNil -> "nil"
    is SString -> value
    is SNumber -> value.toString()
    is SInt -> value.toString()
    is SBool -> value.toString()
    is STable -> "table@${identityKey().toString(16)}"
    is SList -> "list@${identityKey().toString(16)}"
    is SEntityRef -> "entityRef${id}"
    is SInstant -> "instant(${instant.identityKey()})"
    is SJvm -> "jvm@${value.identityKey()}"
    is SId -> id.full
}
