package org.lain.engine.script.compilation

import org.lain.engine.script.Callbacks
import org.lain.engine.script.InventoryTab
import org.lain.engine.script.Namespace
import org.lain.engine.script.NamespaceId
import org.lain.engine.script.SystemPhase
import org.lain.engine.util.file.FileSystem

data class Build(
    val namespaces: Map<NamespaceId, Namespace>,
    val callbacks: Callbacks?,
    val rootPhase: SystemPhase,
    val inventoryTab: InventoryTab,
    val time: Long
) {
    fun log() {
        val namespaces = namespaces.values
        FileSystem.LOGGER.info(
            "Скомпилировано {} предметов, {} звуковых событий, {} прогрессий, {} компонентов, {} систем и {} скриптов в пространствах имён {} за {} мл.",
            namespaces.sumOf { it.items.count() },
            namespaces.sumOf { it.sounds.count() },
            namespaces.sumOf { it.progressionAnimations.count() },
            namespaces.sumOf { it.components.count() },
            namespaces.sumOf { it.systems.count() },
            namespaces.sumOf { it.scripts.count() },
            this.namespaces.keys.sortedBy { it.value }.joinToString(separator = ", "),
            time
        )
    }
}