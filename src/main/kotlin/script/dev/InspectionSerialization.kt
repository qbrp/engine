package org.lain.engine.script.dev

import org.lain.engine.util.ecs.componentType
import kotlin.collections.set
import kotlin.reflect.KType

class InspectionSerializationContext(
    var lastId: Int = 0,
    var lastEditTargetId: Int = 0,
    var lastTypeDescriptorId: Int = 0,
    val visited: MutableMap<Int, Int> = mutableMapOf(),
    val debugObjects: MutableMap<Int, InspectionObject> = mutableMapOf(),
    val editTargets: MutableMap<Int, InspectionEditTarget> = mutableMapOf(),
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
    location: InspectionLocation?,
    jvmType: KType?,
    identity: Any? = null,
    transformer: context(InspectionSerializationContext) (editTargetId: kotlin.Int?) -> InspectionObject
): InspectionValue.Reference {
    val identity = identity ?: this
    val editTargetId = location?.live?.let { ctx.editTarget(it) }

    val objectId = ctx.visited[identity.identityKey()] ?: run {
        val id = ctx.lastId++
        ctx.registerObject(id, this, identity)
        ctx.debugObjects[id] = transformer(editTargetId)
        id
    }

    if (jvmType != null) ctx.type(jvmType)

    return InspectionValue.Reference(objectId)
}

context(ctx: InspectionSerializationContext)
fun InspectionValue.asNode(
    editTargetId: Int?,
    location: InspectionLocation?,
    typeDescriptorId: Int? = null,
): InspectionNode {
    val node = InspectionNode(this, editTargetId, typeDescriptorId)
    location?.let { ctx.nodes[it.dto] = node }
    return node
}

fun InspectionSerializationContext.editTarget(path: InspectionPath): Int {
    val id = lastEditTargetId++
    editTargets[id] = InspectionEditTarget(path)
    return id
}

fun InspectionSerializationContext.type(kType: KType): Int {
    kTypeDescriptors[kType]?.let { id -> return id }
    return type(kType.inspectionTypeDescriptor())
}

fun InspectionSerializationContext.type(descriptor: InspectionTypeDescriptor): Int {
    typeDescriptors[lastTypeDescriptorId++] = descriptor
    return lastTypeDescriptorId
}

fun Any.identityKey(): Int = System.identityHashCode(this)
