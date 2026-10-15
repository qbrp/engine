package org.lain.engine.script.dev

import kotlinx.serialization.Serializable
import org.lain.cyberia.ecs.Component
import org.lain.cyberia.ecs.ComponentType
import org.lain.cyberia.ecs.getComponent
import org.lain.cyberia.ecs.setComponent
import org.lain.engine.script.ScriptComponent
import org.lain.engine.world.World
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor

data class InspectionEditTarget(
    val path: InspectionPath,
)

data class InspectionPath(
    val componentType: ComponentType<*>,
    val segments: List<Segment>,
) {
    fun withSegment(segment: Segment): InspectionPath {
        return copy(segments = segments + segment)
    }

    sealed interface Segment {
        data class Field(val name: String) : Segment
        data class Index(val index: Int) : Segment
        data class TableEntry(val key: Any?) : Segment
    }
}

@Serializable
data class InspectionPathDto(
    val componentType: String,
    val segments: List<Segment>,
) {
    fun withSegment(segment: Segment): InspectionPathDto {
        return copy(segments = segments + segment)
    }

    @Serializable
    sealed interface Segment {
        @Serializable
        data class Field(val name: String) : Segment

        @Serializable
        data class Index(val index: Int) : Segment

        @Serializable
        data class TableKey(
            val key: InspectionValue,
        ) : Segment
    }
}

data class InspectionLocation(
    val live: InspectionPath,
    val dto: InspectionPathDto,
) {
    fun field(name: String) = InspectionLocation(
        live.withSegment(InspectionPath.Segment.Field(name)),
        dto.withSegment(InspectionPathDto.Segment.Field(name)),
    )

    fun index(index: Int) = InspectionLocation(
        live.withSegment(InspectionPath.Segment.Index(index)),
        dto.withSegment(InspectionPathDto.Segment.Index(index)),
    )

    fun tableEntry(liveKey: Any?, inspectionKey: InspectionValue) = InspectionLocation(
        live.withSegment(InspectionPath.Segment.TableEntry(liveKey)),
        dto.withSegment(InspectionPathDto.Segment.TableKey(inspectionKey)),
    )
}

interface InspectionEditNode {
    val value: Any?
    val mapLike: MapLike? get() = null
    val collectionLike: CollectionLike? get() = null

    fun segment(property: InspectionPrimitive): InspectionPath.Segment
    fun child(segment: InspectionPath.Segment): InspectionEditNode

    /**
     * Возвращает актуальное значение контейнера:
     * тот же объект после мутации либо его изменённую копию.
     */
    fun setChild(
        segment: InspectionPath.Segment,
        newValue: InputValue?,
    ): InspectionEditResult

    fun replaceChild(
        segment: InspectionPath.Segment,
        newValue: Any?,
    ): InspectionEditResult

    interface MapLike {
        fun addEntry(key: InputValue, value: InputValue): InspectionEditResult
    }

    interface CollectionLike {
        fun addEntry(value: InputValue): InspectionEditResult
    }
}

sealed interface InspectionEditResult {
    data object MutatedInPlace : InspectionEditResult
    data class Replaced(val value: Any?) : InspectionEditResult
}

fun Any?.toInspectionEditNode(
    declaredType: KType? = null,
): InspectionEditNode {
    return when (this) {
        is ScriptInspectionValue.Table -> ScriptTableEditNode(this)
        is ScriptInspectionValue.List -> ScriptListEditNode(this)
        is ScriptInspectionValue.Jvm -> ScriptJvmEditNode(this)
        is ScriptInspectionValue.Primitive -> error("Cannot descend into script primitive $value")
        is ScriptComponent -> inspectionValueNode.toInspectionEditNode()
        is Map<*, *> -> {
            val typeArguments = declaredType?.arguments
            JvmMapEditNode(this, typeArguments?.get(0)?.type, typeArguments?.get(1)?.type)
        }

        is List<*> -> JvmListEditNode(this, declaredType?.collectionTypeArgumentOrNull())
        null -> error("Cannot descend into null")
        else -> JvmObjectEditNode(this)
    }
}

@Serializable
sealed interface EditOperation {
    @Serializable
    data class SetProperty(val value: InputValue) : EditOperation
    @Serializable
    data class AppendElement(val value: InputValue) : EditOperation
    @Serializable
    data class AppendEntry(val key: InputValue, val value: InputValue) : EditOperation
}

context(world: World)
fun EntityInspection.performEditOperation(
    editTargetId: Int,
    operation: EditOperation
) {
    try {
        val editTarget = lastSnapshot.editTargets[editTargetId]
            ?: error("Inspection edit target $editTargetId not found")
        val path = editTarget.path
        @Suppress("UNCHECKED_CAST")
        val componentType = path.componentType as ComponentType<Component>
        val rootComponent = entity.getComponent<Component>(componentType)
            ?: error("Component ${path.componentType.id} not found")
        val rootNode = rootComponent.toInspectionEditNode()

        when (val result = rootNode.editIn(path.segments, operation)) {
            InspectionEditResult.MutatedInPlace -> Unit
            is InspectionEditResult.Replaced -> {
                @Suppress("UNCHECKED_CAST")
                val componentType = path.componentType
                val updatedRoot = result.value as? Component
                    ?: error("Root replacement is not a component: ${result.value}")
                entity.setComponent(updatedRoot, componentType)
            }
        }
    } catch (e: Exception) {

    }
}


fun InspectionEditNode.editIn(
    segments: List<InspectionPath.Segment>,
    edit: EditOperation,
): InspectionEditResult {
    if (segments.isEmpty()) {
        return performContainerEditOperation(edit)
    }

    val segment = segments.first()
    if (segments.size == 1) {
        return performChildEditOperation(segment, edit)
    }

    return when (val childResult = child(segment).editIn(
        segments.drop(1),
        edit,
    )) {
        InspectionEditResult.MutatedInPlace -> InspectionEditResult.MutatedInPlace
        is InspectionEditResult.Replaced -> replaceChild(segment, childResult.value)
    }
}

private fun InspectionEditNode.performChildEditOperation(
    segment: InspectionPath.Segment,
    edit: EditOperation
): InspectionEditResult {
    return when (edit) {
        is EditOperation.SetProperty -> setChild(segment, edit.value)
        is EditOperation.AppendElement,
        is EditOperation.AppendEntry -> child(segment).performContainerEditOperation(edit)
    }
}

private fun InspectionEditNode.performContainerEditOperation(
    edit: EditOperation,
): InspectionEditResult {
    return when (edit) {
        is EditOperation.AppendElement -> collectionLike
            ?.addEntry(edit.value)
            ?: error("Edit node $this is not CollectionLike")

        is EditOperation.AppendEntry -> mapLike
            ?.addEntry(edit.key, edit.value)
            ?: error("Edit node $this is not MapLike")

        is EditOperation.SetProperty -> error("SetProperty requires a target child")
    }
}

fun Any.copyWith(property: String, value: Any?): Any {
    require(this::class.isData) {
        "${this::class.qualifiedName} is not a data class"
    }

    val constructorParameters = this::class.primaryConstructor
        ?.parameters
        ?: error("Primary constructor not found on ${this::class.qualifiedName}")
    val copyFunction = this::class.memberFunctions
        .singleOrNull { function ->
            function.name == "copy" &&
                function.parameters
                    .filter { it.kind == KParameter.Kind.VALUE }
                    .map { it.name to it.type } ==
                constructorParameters.map { it.name to it.type }
        }
        ?: error("Generated copy function not found on ${this::class.qualifiedName}")

    val propertyParameter = copyFunction.parameters
        .singleOrNull {
            it.kind == KParameter.Kind.VALUE && it.name == property
        }
        ?: error("Copy parameter $property not found")

    return copyFunction.callBy(
        mapOf(
            copyFunction.instanceParameter!! to this,
            propertyParameter to value
        )
    ) ?: error("copy returned null")
}
