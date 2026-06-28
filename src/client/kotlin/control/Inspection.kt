package org.lain.engine.client.control

import org.lain.engine.client.GameSession
import org.lain.engine.world.Hint
import org.lain.engine.world.VoxelPos

data class InspectionMode(
    var concentration: Boolean = false,
    var voxelPos: VoxelPos = VoxelPos(0, 0, 0),
    var inspectHint: InspectHint? = null
) {
    data class InspectHint(
        var hint: Hint,
        var time: Int = 0,
        var totalDuration: Int = 0,
        var selectedText: Int? = null
    ) {
        fun computeText(): Pair<String, Int> {
            val texts = hint.texts
            if (texts.isEmpty()) return "" to 0

            selectedText?.let {
                val idx = it.coerceIn(0, hint.texts.size - 1)
                return hint.texts[idx] to idx
            }

            val weights = texts.map { it.length.coerceAtLeast(1) * 1.3 }
            val totalWeight = weights.sum().toInt()

            val duration = if (totalDuration > 0) totalDuration else totalWeight
            var t = time % duration

            for (i in texts.indices) {
                val segmentTime = (weights[i].toFloat() / totalWeight * duration).toInt()
                if (t < segmentTime) {
                    return texts[i] to i
                }
                t -= segmentTime
            }

            return texts.last() to texts.size - 1
        }
    }
}

fun onScrollInspection(mode: InspectionMode, delta: Float) = mode.inspectHint?.apply {
    if (mode.concentration) {
        if (selectedText == null) {
            selectedText = 0
        }
        val text = selectedText ?: 0

        if (delta > 0) {
            selectedText = text + 1
        } else {
            selectedText = text - 1
        }
        selectedText = selectedText?.coerceIn(0, hint.texts.size - 1)
    }
}

fun GameSession.updateInspectionMode(mode: InspectionMode, inspect: Boolean, hitResultPos: VoxelPos) {
    if (!inspect) {
        mode.inspectHint = null
        return
    }

    if (hitResultPos != mode.voxelPos) {
        mode.voxelPos = hitResultPos
    }
    val blockHint = world.chunkStorage.getBlockHint(mode.voxelPos)
    if (blockHint?.uuid != mode.inspectHint?.hint?.uuid) mode.inspectHint = blockHint?.let { InspectionMode.InspectHint(it) }

    val inspectHint = mode.inspectHint
    if (inspectHint != null) {
        inspectHint.time++
    }
}