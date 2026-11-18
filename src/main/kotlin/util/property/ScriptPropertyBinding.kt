package org.lain.engine.util.property

import org.lain.engine.script.SJvm
import org.lain.engine.script.SNil
import org.lain.engine.script.ScriptValue

class ScriptTableProperyBinding(
    private val table: ScriptValueBinding.Table,
) : PropertyBinding, PropertyBinding.MapLike {
    override val value: Any
        get() = table
    override val mapLike: PropertyBinding.MapLike
        get() = this

    override fun addEntry(key: PropertyInput, value: PropertyInput): PropertyEditResult {
        table.set(key.toScriptValue(), value.toScriptValue())
        return PropertyEditResult.MutatedInPlace
    }

    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        return entry(segment).value.propertyEditNode()
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?,
    ): PropertyEditResult {
        return replaceChild(segment, newValue?.toScriptValue())
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?,
    ): PropertyEditResult {
        table.replace(entry(segment), newValue.toResolvedScriptValue())
        return PropertyEditResult.MutatedInPlace
    }

    private fun entry(segment: PropertyPath.Segment): ScriptValueBinding.Table.Entry {
        require(segment is PropertyPath.Segment.EntryValue) { "Editing table keys is not supported" }
        val key = segment.key as? ScriptValueBinding
            ?: error("Script table path does not contain a script key")
        return table.entries.firstOrNull { it.key.identity == key.identity }
            ?: error("Script table entry not found")
    }
}

class ScriptListPropertyBinding(
    private val list: ScriptValueBinding.List,
) : PropertyBinding, PropertyBinding.CollectionLike {
    override val value: Any
        get() = list
    override val collectionLike: PropertyBinding.CollectionLike
        get() = this

    override fun addEntry(value: PropertyInput): PropertyEditResult {
        list.add(value.toScriptValue())
        return PropertyEditResult.MutatedInPlace
    }

    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        val index = segment.listIndex()
        return list.values.getOrNull(index)?.propertyEditNode()
            ?: error("Script list element $index not found")
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?,
    ): PropertyEditResult {
        return replaceChild(segment, newValue?.toScriptValue())
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?,
    ): PropertyEditResult {
        val index = segment.listIndex()
        require(index in list.values.indices) { "Script list element $index not found" }
        list.set(index, newValue.toResolvedScriptValue())
        return PropertyEditResult.MutatedInPlace
    }
}

class ScriptJvmPropertyBinding(
    private val jvm: ScriptValueBinding.Jvm,
) : PropertyBinding {
    override val value: Any
        get() = jvm.value

    override fun child(segment: PropertyPath.Segment): PropertyBinding {
        return jvm.value.propertyEditNode().child(segment)
    }

    override fun setChild(
        segment: PropertyPath.Segment,
        newValue: PropertyInput?,
    ): PropertyEditResult {
        return jvm.value.propertyEditNode().setChild(segment, newValue)
    }

    override fun replaceChild(
        segment: PropertyPath.Segment,
        newValue: Any?,
    ): PropertyEditResult {
        return jvm.value.propertyEditNode().replaceChild(segment, newValue)
    }
}

private fun Any?.toResolvedScriptValue(): ScriptValue = when (this) {
    null -> SNil
    is ScriptValue -> this
    else -> SJvm(this)
}

private fun PropertyPath.Segment.listIndex(): Int {
    require(this is PropertyPath.Segment.Index)
    return index
}
