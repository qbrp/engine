package org.lain.engine.script.dev

import kotlin.collections.set
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability

class InspectionSerializationContext(
    var lastId: Int = 0,
    var lastEditTargetId: Int = 0,
    var lastTypeDescriptorId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val debugObjects: MutableMap<Int, InspectionObject> = mutableMapOf(),
    val editTargets: MutableMap<Int, InspectionEditTarget> = mutableMapOf(),
    val editTargetIds: MutableMap<InspectionPath, Int> = mutableMapOf(),
    val typeDescriptors: MutableMap<Int, InspectionTypeDescriptor> = mutableMapOf(),
    val kTypeDescriptors: MutableMap<KType, Int> = mutableMapOf(),
    val nodes: MutableMap<InspectionPathDto, InspectionNode> = mutableMapOf(),
) {
    fun registerObject(id: Int, obj: Any, identity: Any = obj) {
        visited[identity.identityKey()] = id
    }
}

context(ctx: InspectionSerializationContext)
fun <T : Any> T.reference(
    identity: Any? = null,
    transformer: context(InspectionSerializationContext) () -> InspectionObject
): InspectionValue.Reference {
    val identity = identity ?: this

    val objectId = ctx.visited[identity.identityKey()] ?: run {
        val id = ctx.lastId++
        ctx.registerObject(id, this, identity)
        ctx.debugObjects[id] = transformer()
        id
    }

    return InspectionValue.Reference(objectId)
}

context(ctx: InspectionSerializationContext)
fun InspectionValue.asNode(
    location: InspectionLocation?,
    typeDescriptorId: Int? = null,
): InspectionNode {
    val editTargetId = location?.live?.let { ctx.editTarget(it) }
    val node = InspectionNode(this, editTargetId, typeDescriptorId)
    location?.let { ctx.nodes[it.dto] = node }
    return node
}

fun InspectionSerializationContext.editTarget(path: InspectionPath): Int {
    editTargetIds[path]?.let { return it }
    val id = lastEditTargetId++
    editTargetIds[path] = id
    editTargets[id] = InspectionEditTarget(path)
    return id
}

fun InspectionSerializationContext.type(kType: KType, createNonNullDescriptor: () -> InspectionTypeDescriptor): Int {
    kTypeDescriptors[kType]?.let { return it }

    val id = lastTypeDescriptorId++
    kTypeDescriptors[kType] = id
    typeDescriptors[id] = InspectionTypeDescriptor.Unknown

    try {
        typeDescriptors[id] = if (kType.isMarkedNullable) {
            val innerType = kType.withNullability(false)
            InspectionTypeDescriptor.Nullable(
                valueTypeId = type(innerType, createNonNullDescriptor),
            )
        } else {
            createNonNullDescriptor()
        }
    } catch (exception: Exception) {
        kTypeDescriptors.remove(kType)
        typeDescriptors.remove(id)
        throw exception
    }

    return id
}

fun InspectionSerializationContext.type(kType: KType): Int {
    val context = this
    val nonNullType = kType.withNullability(false)
    return type(kType) {
        with(context) {
            nonNullType.inspectionTypeDescriptor()
        }
    }
}

fun InspectionSerializationContext.type(
    descriptor: InspectionTypeDescriptor,
): Int {
    val id = lastTypeDescriptorId++
    typeDescriptors[id] = descriptor
    return id
}

fun Any.identityKey(): Int = System.identityHashCode(this)
