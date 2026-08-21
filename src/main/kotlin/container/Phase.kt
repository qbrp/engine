package org.lain.engine.container

import org.lain.engine.world.World
import org.slf4j.LoggerFactory

internal val containerSystemLogger = LoggerFactory.getLogger("Container System")

fun World.tickContainerStateSystem() {
    // состояние
    projectContainerSlotsToEntries()
    tickContainedSystem()
    tickAssignedSlotSystem()
}

fun World.tickContainerOperationsSystem() {
    // операции
    tickTransferOperationValidationSystem()
    tickTransferSetupSystem()
    tickBaseContainerTrasnformOperationSystem()
    tickSlotContainerTransformOperationSystem()
    tickTransformOperationRejectLogSystem()
}

fun World.tickTransferOperationValidationSystem() {
    validateSlotOperation()
    approveUnresolvedTransferOperation()
}

fun World.tickTransferSetupSystem() {
    setupTransferOperationMoveFrom()
    setupTransferOperationSlotDetach()
}