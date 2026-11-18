package org.lain.engine.util.property

import kotlinx.serialization.Serializable
import org.lain.engine.script.ScriptComponent
import org.lain.engine.util.collectionTypeArgumentOrNull
import kotlin.reflect.KType

interface PropertyBinding {
    val value: Any?
    val mapLike: MapLike? get() = null
    val collectionLike: CollectionLike? get() = null

    fun child(segment: PropertyPath.Segment): PropertyBinding

    /**
     * Возвращает актуальное значение контейнера:
     * тот же объект после мутации либо его изменённую копию.
     */
    fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?,
    ): PropertyEditResult

    fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?,
    ): PropertyEditResult

    interface MapLike {
        fun addEntry(key: PropertyInput, value: PropertyInput): PropertyEditResult
    }

    interface CollectionLike {
        fun addEntry(value: PropertyInput): PropertyEditResult
    }
}

sealed interface PropertyEditResult {
    data object MutatedInPlace : PropertyEditResult
    data class Replaced(val value: Any?) : PropertyEditResult
}

@Serializable
sealed interface PropertyEdit {
    @Serializable
    data class Set(val value: PropertyInput) : PropertyEdit // для записей и хэш-мап
    @Serializable
    data class Append(val value: PropertyInput) : PropertyEdit // для коллекций
}

fun Any?.propertyEditNode(
    declaredType: KType? = null,
): PropertyBinding {
    return when (this) {
        is ScriptValueBinding.Table -> ScriptTableProperyBinding(this)
        is ScriptValueBinding.List -> ScriptListPropertyBinding(this)
        is ScriptValueBinding.Jvm -> ScriptJvmPropertyBinding(this)
        is ScriptValueBinding.Primitive -> error("Cannot descend into script scalar $value")
        is ScriptComponent -> inspectionValueNode.propertyEditNode()
        is Map<*, *> -> {
            val typeArguments = declaredType?.arguments
            JvmMapPropertyBinding(this, typeArguments?.get(0)?.type, typeArguments?.get(1)?.type)
        }

        is List<*> -> JvmListPropertyBinding(this, declaredType?.collectionTypeArgumentOrNull())
        null -> error("Cannot descend into null")
        else -> JvmObjectPropertyBinding(this)
    }
}

fun PropertyBinding.edit(
    segments: List<PropertyPath.Segment>,
    edit: PropertyEdit,
): PropertyEditResult {
    if (segments.isEmpty()) {
        return editCollection(edit)
    }

    val segment = segments.first()
    if (segments.size == 1) {
        return editChild(segment, edit)
    }

    return when (val childResult = child(segment).edit(
        segments.drop(1),
        edit,
    )) {
        PropertyEditResult.MutatedInPlace -> PropertyEditResult.MutatedInPlace
        is PropertyEditResult.Replaced -> replaceChild(segment, childResult.value)
    }
}

private fun PropertyBinding.editChild(
    segment: PropertyPath.Segment,
    edit: PropertyEdit
): PropertyEditResult {
    return when (edit) {
        is PropertyEdit.Set -> setChild(segment, edit.value)
        is PropertyEdit.Append -> child(segment).editCollection(edit)
    }
}

private fun PropertyBinding.editCollection(
    edit: PropertyEdit,
): PropertyEditResult {
    return when (edit) {
        is PropertyEdit.Append -> collectionLike
            ?.addEntry(edit.value)
            ?: error("Edit tree $this is not CollectionLike")
        is PropertyEdit.Set -> error("SetProperty requires a target child")
    }
}
