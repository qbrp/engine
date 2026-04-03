package org.lain.engine.player

import kotlinx.serialization.Serializable
import org.lain.engine.chat.EngineChatSettings
import org.lain.engine.chat.MessageSource
import org.lain.engine.chat.OutcomingMessage
import org.lain.engine.chat.distort
import org.lain.engine.server.ServerHandler
import org.lain.engine.server.markDirty
import org.lain.engine.util.component.Component
import org.lain.engine.util.component.get
import org.lain.engine.util.component.handle
import org.lain.engine.util.component.require
import org.lain.engine.util.flush
import java.util.*
import kotlin.math.max

@Serializable
data class Tinnitus(
    val trauma: Float,
    val duration: Int,
)

@Serializable
data class Hearing(
    var tinnitus: ActiveTinnitus? = null,
    var loss: Float = 0f
) : Component {
    @Serializable
    data class ActiveTinnitus(val tinnitus: Tinnitus, var elapsed: Int)
}

data class AcousticMessage(
    val message: OutcomingMessage,
    val recipient: MessageSource.Player
)

data class AcousticMessageQueue(val messages: Queue<AcousticMessage>) : Component

private fun EnginePlayer.flushAcousticMessages(todo: (AcousticMessage) -> Unit) {
    get<AcousticMessageQueue>()?.messages?.flush(todo)
}

fun updateHearing(player: EnginePlayer) = player.handle<Hearing>() {
    tinnitus?.let {
        val (trauma, duration) = it.tinnitus
        if (it.elapsed++ > duration) {
            tinnitus = null
        }
        loss = (1f - it.elapsed / duration) * trauma
    }
    if (tinnitus == null) {
        loss = 0f
    }
}

const val TINNITUS_HEAR_THRESHOLD = 0.2f

fun updateAcousticHearing(player: EnginePlayer, handler: ServerHandler, settings: EngineChatSettings) = player.flushAcousticMessages { (message, recipient) ->
    var message = message
    val hearingLoss = player.require<Hearing>().loss
    val distortion = ((hearingLoss - TINNITUS_HEAR_THRESHOLD) / (1f - TINNITUS_HEAR_THRESHOLD)).coerceIn(0f, 1f)
    if (distortion > 0f) {
        message = message.copy(text = message.text.distort(distortion, settings.distortionArtifacts))
    }
    handler.onOutcomingMessage(recipient, message)
}


// МЕХАНИКА ОТКЛЮЧЕНА
fun EnginePlayer.appendTinnitus(tinnitus: Tinnitus) = handle<Hearing> {
    return@handle
    val oldTinnitus = this.tinnitus
    if (oldTinnitus == null) {
        this.tinnitus = Hearing.ActiveTinnitus(tinnitus, 0)
    } else {
        val newDuration = max(oldTinnitus.tinnitus.duration, tinnitus.duration)
        this.tinnitus = Hearing.ActiveTinnitus(
            tinnitus.copy(
                trauma = oldTinnitus.tinnitus.trauma + tinnitus.trauma,
                duration = newDuration.coerceAtLeast(1)
            ),
            (oldTinnitus.elapsed - newDuration).coerceAtLeast(0)
        )
    }
    this@appendTinnitus.markDirty<Hearing>()
}