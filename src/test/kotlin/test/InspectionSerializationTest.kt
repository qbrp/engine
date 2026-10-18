package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.script.dev.InspectionObject
import org.lain.engine.script.dev.InspectionPrimitive
import org.lain.engine.script.dev.InspectionSerializationContext
import org.lain.engine.script.dev.InspectionTypeDescriptor
import org.lain.engine.script.dev.InspectionValue
import org.lain.engine.script.dev.InputValue
import org.lain.engine.script.dev.ScalarKind
import org.lain.engine.script.dev.ScriptInspectionValue
import org.lain.engine.script.dev.inspectJvmValue
import org.lain.engine.script.dev.inspectScriptValue
import org.lain.engine.script.dev.toJvmValue
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

class InspectionSerializationTest {
    @Test
    fun jvmTypeTraversalDescribesEmptyAndNullFields() {
        val context = InspectionSerializationContext()
        val node = with(context) {
            JvmContainer(emptyList(), null).inspectJvmValue(typeOf<JvmContainer>(), null)
        }

        val recordDescriptor = context.descriptor(node) as InspectionTypeDescriptor.Record
        val fields = recordDescriptor.fields.associateBy { it.name }
        val namesDescriptor = context.descriptor(fields.getValue("names").typeId)
            as InspectionTypeDescriptor.Collection
        val countDescriptor = context.descriptor(fields.getValue("count").typeId)
            as InspectionTypeDescriptor.Nullable

        assertEquals(
            InspectionTypeDescriptor.Scalar(ScalarKind.STRING),
            context.descriptor(namesDescriptor.elementTypeId),
        )
        assertEquals(
            InspectionTypeDescriptor.Scalar(ScalarKind.INT),
            context.descriptor(countDescriptor.valueTypeId),
        )

        val record = context.objectValue(node) as InspectionObject.Record
        assertEquals(fields.getValue("names").typeId, record.fields.getValue("names").typeDescriptorId)
        assertEquals(fields.getValue("count").typeId, record.fields.getValue("count").typeDescriptorId)
        assertTrue((context.objectValue(record.fields.getValue("names")) as InspectionObject.Collection).isEmpty)
        assertEquals(InspectionValue.Null, record.fields.getValue("count").value)
    }

    @Test
    fun nullableCollectionTypeIsDescribedWithoutRuntimeValue() {
        val context = InspectionSerializationContext()
        val node = with(context) {
            (null as List<String>?).inspectJvmValue(typeOf<List<String>?>(), null)
        }

        val nullableDescriptor = context.descriptor(node) as InspectionTypeDescriptor.Nullable
        val collectionDescriptor = context.descriptor(nullableDescriptor.valueTypeId)
            as InspectionTypeDescriptor.Collection

        assertEquals(InspectionValue.Null, node.value)
        assertEquals(
            InspectionTypeDescriptor.Scalar(ScalarKind.STRING),
            context.descriptor(collectionDescriptor.elementTypeId),
        )
    }

    @Test
    fun recursiveNullableTypePointsBackToReservedDescriptor() {
        val context = InspectionSerializationContext()
        val node = with(context) {
            RecursiveNode(null).inspectJvmValue(typeOf<RecursiveNode>(), null)
        }

        val recordDescriptor = context.descriptor(node) as InspectionTypeDescriptor.Record
        val nextDescriptor = context.descriptor(recordDescriptor.fields.single().typeId)
            as InspectionTypeDescriptor.Nullable

        assertEquals(node.typeDescriptorId, nextDescriptor.valueTypeId)
    }

    @Test
    fun valueClassesStayTransparentAndEnumsUseNames() {
        val valueContext = InspectionSerializationContext()
        val valueNode = with(valueContext) {
            Label("label").inspectJvmValue(typeOf<Label>(), null)
        }

        assertEquals(
            InspectionTypeDescriptor.Scalar(ScalarKind.STRING),
            valueContext.descriptor(valueNode),
        )
        assertEquals(
            InspectionValue.Primitive(InspectionPrimitive.Str("label")),
            valueNode.value,
        )

        val enumContext = InspectionSerializationContext()
        val enumNode = with(enumContext) {
            TestMode.ENABLED.inspectJvmValue(typeOf<TestMode>(), null)
        }

        assertEquals(
            InspectionTypeDescriptor.Enum(TestMode.entries.map { it.name }),
            enumContext.descriptor(enumNode),
        )
        assertEquals(
            InspectionValue.Primitive(InspectionPrimitive.Enum("ENABLED")),
            enumNode.value,
        )
        assertEquals(
            TestMode.ENABLED,
            InputValue.Primitive(InspectionPrimitive.Enum("ENABLED"))
                .toJvmValue(typeOf<TestMode>()),
        )
    }

    @Test
    fun scriptTraversalDescribesContainersAndTheirValuesIndependently() {
        val emptyContext = InspectionSerializationContext()
        val emptyNode = with(emptyContext) {
            TestScriptList(emptyList()).inspectScriptValue(null)
        }
        val emptyDescriptor = emptyContext.descriptor(emptyNode)
            as InspectionTypeDescriptor.Collection

        assertEquals(
            InspectionTypeDescriptor.Unknown,
            emptyContext.descriptor(emptyDescriptor.elementTypeId),
        )
        assertTrue((emptyContext.objectValue(emptyNode) as InspectionObject.Collection).isEmpty)

        val valueContext = InspectionSerializationContext()
        val valueNode = with(valueContext) {
            TestScriptList(listOf(TestScriptPrimitive(SString("value"))))
                .inspectScriptValue(null)
        }
        val collection = valueContext.objectValue(valueNode) as InspectionObject.Collection
        val child = collection.values.single()

        assertEquals(
            InspectionTypeDescriptor.Scalar(ScalarKind.STRING),
            valueContext.descriptor(child),
        )
        assertEquals(
            InspectionValue.Primitive(InspectionPrimitive.Str("value")),
            child.value,
        )
    }

    @Test
    fun sealedClassesSerializes() {
        val context = InspectionSerializationContext()
        val node = with(context) {
            TestHierarchy.Second(10).inspectJvmValue(
                TestHierarchy::class.starProjectedType,
                null
            )
        }
    }

    private fun InspectionSerializationContext.descriptor(node: org.lain.engine.script.dev.InspectionNode) =
        descriptor(node.typeDescriptorId ?: error("Node does not have a type descriptor"))

    private fun InspectionSerializationContext.descriptor(id: Int): InspectionTypeDescriptor =
        typeDescriptors[id] ?: error("Type descriptor $id not found")

    private fun InspectionSerializationContext.objectValue(
        node: org.lain.engine.script.dev.InspectionNode,
    ): InspectionObject {
        val reference = node.value as InspectionValue.Reference
        return debugObjects[reference.objectId] ?: error("Inspection object ${reference.objectId} not found")
    }

    data class JvmContainer(
        val names: List<String>,
        val count: Int?,
    )

    data class RecursiveNode(
        val next: RecursiveNode?,
    )

    @JvmInline
    value class Label(val value: String)

    enum class TestMode {
        ENABLED,
        DISABLED,
    }

    sealed class TestHierarchy {
        data class First(val value: Int) : TestHierarchy()
        data class Second(val value: Int) : TestHierarchy()
    }

    private data class TestScriptPrimitive(
        override val value: ScriptValuePrimitive,
    ) : ScriptInspectionValue.Primitive

    private data class TestScriptList(
        override val values: List<ScriptInspectionValue>,
    ) : ScriptInspectionValue.List {
        override fun set(index: Int, value: ScriptValue) = unsupported()
        override fun add(value: ScriptValue) = unsupported()

        private fun unsupported(): Nothing = error("Mutation is not used by serialization tests")
    }
}
