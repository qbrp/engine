package org.lain.engine.client.handler

import org.lain.engine.data.PersistentId
import org.lain.engine.server.protocolError
import org.lain.engine.server.replication.ReplicationSnapshot

internal data class ReplicatedComponentKey(
    val entity: PersistentId,
    val componentTypeId: String,
)

internal sealed interface PredictedComponentState {
    data class Present(val snapshot: ReplicationSnapshot) : PredictedComponentState
    data object Removed : PredictedComponentState
}

internal data class PredictionFrame(
    val inputTick: Long,
    val components: MutableMap<ReplicatedComponentKey, PredictedComponentState>,
)

internal data class EntityComponentApplication(
    val updated: List<ReplicationSnapshot> = emptyList(),
    val removed: Set<String> = emptySet(),
)

/**
 * Хранит снимки изменённых предсказанием компонентов по input tick.
 * При подтверждении сравнивает их с авторитетным состоянием и возвращает только изменения,
 * которые действительно нужно перенести в live ECS.
 */
internal class ClientPredictionState {
    // Пока кадр выполняется, запоминаются только ключи изменений; итоговые значения снимаются в конце тика.
    private data class ActivePrediction(
        val inputTick: Long,
        val allowedEntities: Set<PersistentId>,
        val changedComponents: MutableSet<ReplicatedComponentKey> = mutableSetOf(),
    )

    private data class MutableEntityComponentApplication(
        val updated: MutableMap<String, ReplicationSnapshot> = mutableMapOf(),
        val removed: MutableSet<String> = mutableSetOf(),
    ) {
        fun update(snapshot: ReplicationSnapshot) {
            removed -= snapshot.id
            updated[snapshot.id] = snapshot
        }

        fun remove(componentTypeId: String) {
            updated -= componentTypeId
            removed += componentTypeId
        }

        fun freeze() = EntityComponentApplication(updated.values.toList(), removed.toSet())
    }

    // Неподтверждённые кадры всегда расположены по возрастанию inputTick.
    private val predictionFrames = ArrayDeque<PredictionFrame>()
    private var activePrediction: ActivePrediction? = null
    private var lastConfirmedInputTick: Long = -1

    fun clear() {
        predictionFrames.clear()
        activePrediction = null
        lastConfirmedInputTick = -1
    }

    fun startPrediction(inputTick: Long, entities: Set<PersistentId>) {
        check(activePrediction == null) { "Prediction frame ${activePrediction?.inputTick} is still active" }
        val previousTick = predictionFrames.lastOrNull()?.inputTick ?: lastConfirmedInputTick
        check(inputTick > previousTick) {
            "Prediction ticks must increase: previous=$previousTick, next=$inputTick"
        }

        activePrediction = ActivePrediction(inputTick, entities)
    }

    fun onComponentChange(
        entityId: PersistentId,
        componentTypeId: String,
    ) {
        val prediction = activePrediction ?: return
        if (entityId !in prediction.allowedEntities) return

        prediction.changedComponents += ReplicatedComponentKey(entityId, componentTypeId)
    }

    fun endPrediction(snapshotComponent: (PersistentId, String) -> ReplicationSnapshot?) {
        val prediction = checkNotNull(activePrediction) { "Prediction is not active" }
        val components = prediction.changedComponents.associateWithTo(linkedMapOf()) { key ->
            snapshotComponent(key.entity, key.componentTypeId)
                ?.let(PredictedComponentState::Present)
                ?: PredictedComponentState.Removed
        }
        predictionFrames += PredictionFrame(prediction.inputTick, components)
        activePrediction = null
    }

    fun invalidatePredictions(persistentId: PersistentId) {
        activePrediction?.changedComponents?.removeIf { it.entity == persistentId }
        predictionFrames.forEach { frame ->
            frame.components.keys.removeIf { it.entity == persistentId }
        }
    }

    fun resolveFrame(
        accepted: Map<PersistentId, SnapshotAcceptance.Accepted>,
        processedInputTick: Long?,
        authoritativeComponent: (PersistentId, String) -> ReplicationSnapshot?,
    ): Map<PersistentId, EntityComponentApplication> {
        val applications = mutableMapOf<PersistentId, MutableEntityComponentApplication>()
        val confirmedStates = processedInputTick?.let(::confirmInput).orEmpty()
        val corrections = mutableSetOf<ReplicatedComponentKey>()

        // Подтверждённое предсказание сохраняется в live ECS только при точном совпадении с сервером.
        confirmedStates.forEach { (key, predictedState) ->
            val authoritative = authoritativeComponent(key.entity, key.componentTypeId)
            if (!predictedState.matches(authoritative)) {
                corrections += key
            }
        }

        // Ошибка одного компонента отменяет его более новые предсказания; остальные остаются защищены от отката.
        corrections.forEach(::invalidateComponentPredictions)
        val protectedComponents = confirmedStates.keys.toMutableSet()
            predictionFrames.forEach { frame -> protectedComponents += frame.components.keys }
        activePrediction?.let { prediction -> protectedComponents += prediction.changedComponents }

        // Задержавшееся серверное значение применяется только к компонентам без действующего предсказания.
        accepted.forEach { (persistentId, snapshot) ->
            val application = applications.getOrPut(persistentId) { MutableEntityComponentApplication() }

            fun isProtected(componentTypeId: String) =
                ReplicatedComponentKey(persistentId, componentTypeId) in protectedComponents

            snapshot.updated
                .filterNot { isProtected(it.id) }
                .forEach(application::update)

            snapshot.removed
                .filterNot(::isProtected)
                .forEach(application::remove)
        }
        // Для расхождений авторитетное значение применяется безусловно.
        corrections.forEach { key ->
            val application = applications.getOrPut(key.entity) { MutableEntityComponentApplication() }
            authoritativeComponent(key.entity, key.componentTypeId)?.let(application::update)
                ?: application.remove(key.componentTypeId)
        }

        return applications.mapValues { (_, application) -> application.freeze() }
    }

    private fun confirmInput(inputTick: Long): Map<ReplicatedComponentKey, PredictedComponentState> {
        if (inputTick <= lastConfirmedInputTick) return emptyMap()

        val confirmed = linkedMapOf<ReplicatedComponentKey, PredictedComponentState>()
        var foundInputTick = false
        // Сервер подтверждает всё до inputTick включительно; более позднее значение компонента заменяет раннее.
        while (predictionFrames.firstOrNull()?.inputTick?.let { it <= inputTick } == true) {
            val frame = predictionFrames.removeFirst()
            confirmed.putAll(frame.components)
            if (frame.inputTick == inputTick) {
                foundInputTick = true
            }
        }

        if (!foundInputTick) {
            val nextTick = predictionFrames.firstOrNull()?.inputTick
            protocolError("Missing prediction frame for acknowledged input tick $inputTick, next=$nextTick")
        }

        lastConfirmedInputTick = inputTick
        return confirmed
    }

    private fun invalidateComponentPredictions(key: ReplicatedComponentKey) {
        activePrediction?.changedComponents?.remove(key)
        predictionFrames.forEach { frame -> frame.components -= key }
    }

    private fun PredictedComponentState.matches(authoritative: ReplicationSnapshot?): Boolean = when (this) {
        is PredictedComponentState.Present -> snapshot == authoritative
        PredictedComponentState.Removed -> authoritative == null
    }
}
