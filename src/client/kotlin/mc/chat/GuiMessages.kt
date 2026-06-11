package org.lain.engine.client.mc.chat

import net.minecraft.client.gui.components.ComponentRenderUtils
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import org.lain.engine.client.chat.AcceptedMessage
import org.lain.engine.client.mc.MinecraftClient
import org.lain.engine.client.mc.parseMiniMessageClient
import org.lain.engine.item.ItemStorage
import org.lain.engine.mc.Text
import org.lain.engine.mc.literalText
import org.lain.engine.script.ThreadSafeNamespaceStorageAccessImpl
import org.lain.engine.script.emptyNamespacedStorage
import org.lain.engine.world.WorldId
import org.lain.engine.world.world

const val MAXIMUM_REPEATS_TEXT = "x999"

data class EngineChatHudMessage(
    val author: PlayerInfo?,
    val engineMessage: AcceptedMessage,
    val addedTime: Int,
) {
    val debugText: Text? by lazy {
        val volume = engineMessage.volume
        volume?.let { (input, result) -> literalText(" [input $input, result $result]") }
    }

    fun resolveLines(width: Int): List<EngineChatHudLine> {
        val chatManager = MinecraftChat.chatManager
        val font = MinecraftClient.font
        val text = when(chatManager?.allhear ?: false) {
            false -> engineMessage.displayText
            true -> engineMessage.undistortedDisplayText
        }
        val lines = when(engineMessage.vanilla != null) {
            false -> ComponentRenderUtils.wrapComponents(
                text.parseMiniMessageClient(),
                width - font.width(MAXIMUM_REPEATS_TEXT),
                font
            )
            true -> engineMessage.vanilla.splitLines(font, width)
        }
        return lines.mapIndexed { i, line ->
            EngineChatHudLine(this, line, i == 0, i == lines.lastIndex)
        }
    }
}

data class EngineChatHudLine(
    val message: EngineChatHudMessage,
    val text: FormattedCharSequence,
    val isFirst: Boolean,
    val isLast: Boolean
)

fun interface EngineAlphaCalculator {
    fun calculate(line: EngineChatHudLine): Float

    companion object {
        fun timeBased(i: Int): EngineAlphaCalculator {
            return EngineAlphaCalculator { line: EngineChatHudLine ->
                val j = i - line.message.addedTime
                var d = j / 200.0
                d = 1.0 - d
                d *= 10.0
                d = Mth.clamp(d, 0.0, 1.0)
                d *= d
                d.toFloat()
            }
        }

        val FULLY_VISIBLE: EngineAlphaCalculator = EngineAlphaCalculator { line: EngineChatHudLine -> 1.0f }
    }
}

fun interface EngineLineConsumer {
    fun accept(line: EngineChatHudLine, lineIndex: Int, alpha: Float)
}

fun DummyWorld() = world(
    WorldId("dummy"),
    Thread.currentThread(),
    ItemStorage(),
    ThreadSafeNamespaceStorageAccessImpl(
        emptyNamespacedStorage()
    )
)