package org.lain.engine.script.dev

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
import kotlin.collections.component1
import kotlin.collections.component2

context(ctx: InspectionSerializationContext)
fun ScriptValue.toScriptInspectionValue(
    readonly: Boolean,
    target: ScriptInspectionTarget? = null
): InspectionValue {
    return when (this) {
        SNil -> Null
        is STable -> Reference(appendSerializationContext(target) { toScriptInspectionObject(target) })
        is SString -> Primitive(readonly, Str(value))
        is SNumber -> Primitive(readonly, Double(value))
        is SBool -> Primitive(readonly, Bool(value))
        is SInt -> Primitive(readonly, Int(value))
        is SList -> Reference(appendSerializationContext(target) { toScriptInspectionObject(target) })
        is SEntityRef -> Primitive(readonly, Int(id))
        is SInstant -> Primitive(
            readonly, Str(
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
                )
            )
        )
        is SId -> Primitive(readonly, Id(id))
        is SJvm -> {
            Reference(appendSerializationContext(null) { value.toJvmInspectionObject() })
        }
    }
}

context(ctx: InspectionSerializationContext)
private fun STable.toScriptInspectionObject(target: ScriptInspectionTarget?) = InspectionObject.Table(
    values.entries.associate { (key, value) ->
        val childTarget = if (value is STable || value is SList) target?.child(key) else null
        key.toInspectionKey() to value.toScriptInspectionValue(target == null, childTarget)
    }
)

context(ctx: InspectionSerializationContext)
private fun SList.toScriptInspectionObject(target: ScriptInspectionTarget?) = InspectionObject.Collection(
    values.mapIndexed { idx, value ->
        val childTarget = if (value is STable || value is SList) target?.indexedChild(idx + 1) else null
        value.toScriptInspectionValue(target == null, childTarget)
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