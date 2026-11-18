package org.lain.engine.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lain.engine.script.SString
import org.lain.engine.script.ScriptValue
import org.lain.engine.script.ScriptValuePrimitive
import org.lain.engine.util.property.PropertyCompound
import org.lain.engine.util.property.Property
import org.lain.engine.util.property.PropertyLocation
import org.lain.engine.util.property.PropertyScalar
import org.lain.engine.util.property.PropertyTreeBuilder
import org.lain.engine.util.property.PropertyTypeDescriptor
import org.lain.engine.util.property.PropertyTypeDescriptorId
import org.lain.engine.util.property.PropertyValue
import org.lain.engine.util.property.PropertyInput
import org.lain.engine.util.property.ScalarKind
import org.lain.engine.util.property.ScriptValueBinding
import org.lain.engine.util.property.jvmProperty
import org.lain.engine.util.property.inspectScriptValue
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf

class InspectionSerializationTest : EngineTest() {
    @Test
    fun jvmTypeTraversalDescribesEmptyAndNullFields() {
        val context = PropertyTreeBuilder(TestComponentWorld())
        val node = with(context) {
            JvmContainer(emptyList(), null).jvmProperty(typeOf<JvmContainer>(), PropertyLocation())
        }

        val recordDescriptor = context.descriptor(node) as PropertyTypeDescriptor.Record
        val fields = recordDescriptor.fields.associateBy { it.name }
        val namesDescriptor = context.descriptor(fields.getValue("names").type)
            as PropertyTypeDescriptor.Collection
        val countDescriptor = context.descriptor(fields.getValue("count").type)
            as PropertyTypeDescriptor.Nullable

        assertEquals(
            PropertyTypeDescriptor.Scalar(ScalarKind.STRING),
            context.descriptor(namesDescriptor.elementType),
        )
        assertEquals(
            PropertyTypeDescriptor.Scalar(ScalarKind.INT),
            context.descriptor(countDescriptor.valueTypeId),
        )

        val record = context.objectValue(node) as PropertyCompound.Record
        assertEquals(fields.getValue("names").type, record.fields.getValue("names").type)
        assertEquals(fields.getValue("count").type, record.fields.getValue("count").type)
        assertTrue((context.objectValue(record.fields.getValue("names")) as PropertyCompound.Collection).isEmpty)
        assertNull(record.fields.getValue("count").value)
    }

    @Test
    fun nullableCollectionTypeIsDescribedWithoutRuntimeValue() {
        val context = PropertyTreeBuilder(TestComponentWorld())
        val node = with(context) {
            (null as List<String>?).jvmProperty(typeOf<List<String>?>(), PropertyLocation())
        }

        val nullableDescriptor = context.descriptor(node) as PropertyTypeDescriptor.Nullable
        val collectionDescriptor = context.descriptor(nullableDescriptor.valueTypeId)
            as PropertyTypeDescriptor.Collection

        assertNull(node.value)
        assertEquals(
            PropertyTypeDescriptor.Scalar(ScalarKind.STRING),
            context.descriptor(collectionDescriptor.elementType),
        )
    }

    @Test
    fun recursiveNullableTypePointsBackToReservedDescriptor() {
        val context = PropertyTreeBuilder(TestComponentWorld())
        val node = with(context) {
            RecursiveNode(null).jvmProperty(typeOf<RecursiveNode>(), PropertyLocation())
        }

        val recordDescriptor = context.descriptor(node) as PropertyTypeDescriptor.Record
        val nextDescriptor = context.descriptor(recordDescriptor.fields.single().type)
            as PropertyTypeDescriptor.Nullable

        assertEquals(node.type, nextDescriptor.valueTypeId)
    }

    @Test
    fun valueClassesStayTransparentAndEnumsUseNames() {
        val valueContext = PropertyTreeBuilder(TestComponentWorld())
        val valueNode = with(valueContext) {
            Label("label").jvmProperty(typeOf<Label>(), PropertyLocation())
        }

        assertEquals(
            PropertyTypeDescriptor.Scalar(ScalarKind.STRING),
            valueContext.descriptor(valueNode),
        )
        assertEquals(
            PropertyValue.Primitive(PropertyScalar.Str("label")),
            valueNode.value,
        )

        val enumContext = PropertyTreeBuilder(TestComponentWorld())
        val enumNode = with(enumContext) {
            TestMode.ENABLED.jvmProperty(typeOf<TestMode>(), PropertyLocation())
        }

        assertEquals(
            PropertyTypeDescriptor.Enum(TestMode::class.qualifiedName!!, TestMode.entries.map { it.name }),
            enumContext.descriptor(enumNode),
        )
        assertEquals(
            PropertyValue.Primitive(PropertyScalar.Enum("ENABLED")),
            enumNode.value,
        )
        assertEquals(
            TestMode.ENABLED,
            PropertyInput.Scalar(PropertyScalar.Enum("ENABLED"))
                .toJvmValue(typeOf<TestMode>()),
        )
    }

    @Test
    fun scriptTraversalDescribesContainersAndTheirValuesIndependently() {
        val emptyContext = PropertyTreeBuilder(TestComponentWorld())
        val emptyNode = with(emptyContext) {
            TestScriptListBinding(emptyList()).inspectScriptValue(PropertyLocation())
        }
        val emptyDescriptor = emptyContext.descriptor(emptyNode)
            as PropertyTypeDescriptor.Collection

        assertEquals(
            PropertyTypeDescriptor.Unknown,
            emptyContext.descriptor(emptyDescriptor.elementType),
        )
        assertTrue((emptyContext.objectValue(emptyNode) as PropertyCompound.Collection).isEmpty)

        val valueContext = PropertyTreeBuilder(TestComponentWorld())
        val valueNode = with(valueContext) {
            TestScriptListBinding(listOf(TestScriptPrimitiveBinding(SString("value"))))
                .inspectScriptValue(PropertyLocation())
        }
        val collection = valueContext.objectValue(valueNode) as PropertyCompound.Collection
        val child = collection.values.single()

        assertEquals(
            PropertyTypeDescriptor.Scalar(ScalarKind.STRING),
            valueContext.descriptor(child),
        )
        assertEquals(
            PropertyValue.Primitive(PropertyScalar.Str("value")),
            child.value,
        )
    }

    @Test
    fun sealedClassesSerializes() {
        val context = PropertyTreeBuilder(TestComponentWorld())
        val node = with(context) {
            TestHierarchy.Second(10).jvmProperty(
                TestHierarchy::class.starProjectedType,
                PropertyLocation()
            )
        }
    }

    private fun PropertyTreeBuilder.descriptor(node: Property) = descriptor(node.type)

    private fun PropertyTreeBuilder.descriptor(id: PropertyTypeDescriptorId): PropertyTypeDescriptor =
        typeDescriptors[id] ?: error("Type descriptor $id not found")

    private fun PropertyTreeBuilder.objectValue(
        node: Property,
    ): PropertyCompound {
        val reference = node.value as PropertyValue.Reference
        return compounds[reference.objectId] ?: error("Inspection object ${reference.objectId} not found")
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

    private data class TestScriptPrimitiveBinding(
        override val value: ScriptValuePrimitive,
    ) : ScriptValueBinding.Primitive

    private data class TestScriptListBinding(
        override val values: List<ScriptValueBinding>,
    ) : ScriptValueBinding.List {
        override fun set(index: Int, value: ScriptValue) = unsupported()
        override fun add(value: ScriptValue) = unsupported()

        private fun unsupported(): Nothing = error("Mutation is not used by serialization tests")
    }
}
