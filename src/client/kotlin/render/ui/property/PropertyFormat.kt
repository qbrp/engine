package org.lain.engine.client.render.ui.property

import imgui.ImGui
import org.lain.cyberia.ecs.EntityId
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.PropertyReference
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.ScalarKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.component1
import kotlin.collections.component2

context(editor: PropertyEditor)
fun PropertyCompound.formatString(
    ancestors: Set<PropertyReference> = emptySet(),
    depth: Int = 0,
): String? {
    fun threeDots() = "...".takeIf { depth > 1 }

    return when (this) {
        is PropertyCompound.Collection -> "[ " + (threeDots() ?: values.joinToString { it.value.formatString(ancestors, depth) }) + " ]"
        is PropertyCompound.PlayerPhysics -> null
        is PropertyCompound.Player -> description
        is PropertyCompound.Table -> {
            "{ " + (threeDots() ?: entries.map { (k, v) -> k.value.formatString(ancestors) to v }.formatString(ancestors, depth)) + " }"
        }
        is PropertyCompound.Record ->
            "{ " + (threeDots() ?: fields.map { (property, node) -> property to node }.formatString(ancestors, depth)) + " }"
    }
}

context(editor: PropertyEditor)
fun List<Pair<String, Property>>.formatString(ancestors: Set<PropertyReference>, depth: Int = 0): String {
    return joinToString { (key, value) -> "${key}: ${value.value.formatString(ancestors, depth)}" }
}

context(editor: PropertyEditor)
fun PropertyValue?.formatString(ancestors: Set<PropertyReference>, depth: Int = 0): String {
    return when (this) {
        null -> "null"
        is PropertyValue.Primitive -> primitive.formatString()
        is PropertyValue.Reference -> {
            if (objectId in ancestors) {
                "ERROR: cyclic reference ${objectId.value}"
            } else {
                val inspectionObject = editor.tree.compounds[objectId]
                inspectionObject?.formatString(ancestors + objectId, depth + 1) ?: "ERROR: data not found"
            }
        }
    }
}

context(editor: PropertyEditor)
fun PropertyScalar.formatString(): String {
    return when (this) {
        is PropertyScalar.Bool -> bool.toString()
        is PropertyScalar.Decimal -> {
            if (editor.shortDoubleFormatting.get()) {
                "%.2f".format(this.toDouble())
            } else {
                when (this) {
                    is PropertyScalar.Double -> string
                    is PropertyScalar.Float -> string
                    else -> toDouble().toString()
                }
            }
        }

        is PropertyScalar.Int -> string
        is PropertyScalar.Str -> string
        is PropertyScalar.Uuid -> string
        is PropertyScalar.Id -> id.full
        is PropertyScalar.Char -> char.toString()
        is PropertyScalar.Enum -> name
        is PropertyScalar.EntityRef ->
            debugName?.let { "$it+${entityId.formatString()}" }
                ?: persistentId?.let { "$it+${entityId.formatString()}" }
                ?: entityId.formatString()

        is PropertyScalar.Instant -> LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()
        ).format(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
        )
    }
}

private fun EntityId.formatString() = "$index-$generation"

context(editor: PropertyEditor)
fun PropertyTypeDescriptor.formatString(fullName: Boolean = false): String = when (this) {
    is PropertyTypeDescriptor.Collection ->
        "Collection<${editor.resolveType(elementType).formatString(fullName)}>"

    is PropertyTypeDescriptor.Enum -> if (fullName) name else name.substringAfterLast('.')
    is PropertyTypeDescriptor.Map ->
        "Map<${editor.resolveType(keyType).formatString(fullName)}, ${
            editor.resolveType(valueType).formatString(fullName)
        }>"

    is PropertyTypeDescriptor.Nullable -> "${editor.resolveType(valueTypeId).formatString(fullName)}?"
    is PropertyTypeDescriptor.Record -> if (fullName) name else name.substringAfterLast('.')
    is PropertyTypeDescriptor.Scalar -> when (kind) {
        ScalarKind.STRING -> "String"
        ScalarKind.CHAR -> "Char"
        ScalarKind.INT -> "Int"
        ScalarKind.DOUBLE -> "Double"
        ScalarKind.FLOAT -> "Float"
        ScalarKind.BOOL -> "Boolean"
        ScalarKind.UUID -> "UUID"
        ScalarKind.ID -> "Id"
        ScalarKind.ENTITY_REF -> "Entity"
        ScalarKind.INSTANT -> "Timestamp"
    }

    PropertyTypeDescriptor.Unknown -> "unknown"
}