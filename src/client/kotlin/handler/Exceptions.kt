package org.lain.engine.client.handler

import org.lain.engine.data.PersistentId
import org.lain.engine.util.EntityDebugId

class ComponentReviveException(
    val componentTypeId: String,
    cause: Throwable,
) : RuntimeException(
    "Не удалось восстановить снимок компонента $componentTypeId",
    cause
)

class ComponentRemoveException(
    componentTypeId: String,
    cause: Throwable
) : RuntimeException(
    "Не удалось удалить компонент $componentTypeId",
    cause
)

class ReplicationSnapshotApplyException(
    val debugId: EntityDebugId,
    val entityPersistentId: PersistentId,
    val baseRevision: Long?,
    val targetRevision: Long,
    cause: Throwable,
) : RuntimeException(
    "Не удалось применить снимок сущности ${debugId.name} ($entityPersistentId), " +
        "ревизия $baseRevision->$targetRevision",
    cause,
)
