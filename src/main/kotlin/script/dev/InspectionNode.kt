package org.lain.engine.script.dev

import kotlinx.serialization.Serializable

@Serializable
data class InspectionNode(
    val value: InspectionValue,
    val editTargetId: Int? = null,
    val typeDescriptorId: Int? = null,
)

fun InspectionValue.asNode(editTargetId: Int?) = InspectionNode(this, editTargetId)