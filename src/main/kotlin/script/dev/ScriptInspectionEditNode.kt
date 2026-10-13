package org.lain.engine.script.dev

import org.lain.engine.script.SJvm
import org.lain.engine.script.SNil
import org.lain.engine.script.ScriptValue

class ScriptTableEditNode(
    private val table: ScriptInspectionValue.Table,
) : InspectionEditNode, InspectionEditNode.MapLike {
    override val value: Any
        get() = table
    override val mapLike: InspectionEditNode.MapLike
        get() = this

    override fun addEntry(key: InputValue, value: InputValue): InspectionEditResult {
        table.set(key.toScriptValue(), value.toScriptValue())
        return InspectionEditResult.MutatedInPlace
    }

    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        val keyValue = property.toScriptValue()
        val key = table.entries.firstOrNull {
            (it.key as? ScriptInspectionValue.Primitive)?.value == keyValue
        }?.key ?: error("Script table entry for key $keyValue not found")
        return InspectionPath.Segment.TableEntry(key)
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        return entry(segment).value.toInspectionEditNode()
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?,
    ): InspectionEditResult {
        return replaceChild(segment, newValue.toScriptValue())
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?,
    ): InspectionEditResult {
        table.replace(entry(segment), newValue.toResolvedScriptValue())
        return InspectionEditResult.MutatedInPlace
    }

    private fun entry(segment: InspectionPath.Segment): ScriptInspectionValue.Table.Entry {
        require(segment is InspectionPath.Segment.TableEntry)
        val key = segment.key as? ScriptInspectionValue
            ?: error("Script table path does not contain a script key")
        return table.entries.firstOrNull { it.key.identity == key.identity }
            ?: error("Script table entry not found")
    }
}

class ScriptListEditNode(
    private val list: ScriptInspectionValue.List,
) : InspectionEditNode, InspectionEditNode.CollectionLike {
    override val value: Any
        get() = list
    override val collectionLike: InspectionEditNode.CollectionLike?
        get() = this

    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        val index = (property as? InspectionPrimitive.Int)?.int
            ?: error("Script list index must be an integer")
        return InspectionPath.Segment.Index(index)
    }

    override fun addEntry(value: InputValue): InspectionEditResult {
        list.add(value.toScriptValue())
        return InspectionEditResult.MutatedInPlace
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        val index = segment.listIndex()
        return list.values.getOrNull(index)?.toInspectionEditNode()
            ?: error("Script list element $index not found")
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?,
    ): InspectionEditResult {
        return replaceChild(segment, newValue.toScriptValue())
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?,
    ): InspectionEditResult {
        val index = segment.listIndex()
        require(index in list.values.indices) { "Script list element $index not found" }
        list.set(index, newValue.toResolvedScriptValue())
        return InspectionEditResult.MutatedInPlace
    }
}

class ScriptJvmEditNode(
    private val jvm: ScriptInspectionValue.Jvm,
) : InspectionEditNode {
    override val value: Any
        get() = jvm.value

    override fun segment(property: InspectionPrimitive): InspectionPath.Segment {
        return jvm.value.toInspectionEditNode().segment(property)
    }

    override fun child(segment: InspectionPath.Segment): InspectionEditNode {
        return jvm.value.toInspectionEditNode().child(segment)
    }

    override fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?,
    ): InspectionEditResult {
        return jvm.value.toInspectionEditNode().setChild(segment, newValue)
    }

    override fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?,
    ): InspectionEditResult {
        return jvm.value.toInspectionEditNode().replaceChild(segment, newValue)
    }
}

private fun Any?.toResolvedScriptValue(): ScriptValue = when (this) {
    null -> SNil
    is ScriptValue -> this
    else -> SJvm(this)
}

private fun InspectionPath.Segment.listIndex(): Int {
    require(this is InspectionPath.Segment.Index)
    return index
}
