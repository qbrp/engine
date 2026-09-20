package org.lain.engine.util.ecs

import kotlinx.serialization.Serializable
import org.lain.engine.data.PersistentId
import org.lain.engine.script.EngineId

enum class RelationKind {
    OWNERSHIP
}

data class RelationType(
    val id: RelationTypeId,
    val kind: RelationKind
)

@JvmInline
@Serializable
value class RelationTypeId(val id: EngineId) {
    override fun toString(): String = id.toString()
}

fun EngineId.toRelationTypeId(): RelationTypeId = RelationTypeId(this)

interface EntityRelation {
    val id: RelationTypeId
    val child: PersistentId
}

suspend fun traverseEntityGraph(
    root: PersistentId,
    rootRelations: List<EntityRelation>,
    visit: suspend (PersistentId) -> List<EntityRelation>?,
) {
    val visited = mutableSetOf<PersistentId>()
    val queue = ArrayDeque<PersistentId>()

    var rootRelations: List<EntityRelation>? = rootRelations
    queue += root

    while (queue.isNotEmpty()) {
        val entity = queue.removeFirst()
        if (!visited.add(entity)) {
            continue
        }
        val relations = rootRelations ?: visit(entity) ?: continue
        for (relation in relations) {
            queue += relation.child
        }
        rootRelations = null
    }
}