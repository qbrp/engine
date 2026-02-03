package org.lain.engine.mc

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.WorldChunk
import kotlin.collections.plus

data class BlockHint(
    val title: String,
    val texts: MutableList<String> = mutableListOf()
) {
    fun displayText(index: Int): String {
        val text = texts[index]
        return "<gold>$index:</gold> <gray>$text"
    }
}

typealias ChunkBlockHints = Map<BlockPos, BlockHint>

var WorldChunk.blockHints
    get() = (this as AttachmentTarget).getAttached(BLOCK_HINT_ATTACHMENT_TYPE)
    set(value) {
        (this as AttachmentTarget).setAttached(BLOCK_HINT_ATTACHMENT_TYPE, value)
    }

fun WorldChunk.getBlockHint(pos: BlockPos) = blockHints?.get(pos)

fun WorldChunk.setBlockHint(pos: BlockPos, text: String, index: Int? = null): BlockHint {
    val hints = blockHints!!
    val hint = BlockHint(getBlockState(pos).block.name.string)
    blockHints = hints + mapOf(pos to hint)
    val texts = hint.texts
    when (index) {
        null -> texts.add(text)
        in texts.indices -> texts[index] = text
    }
    return hint
}

fun WorldChunk.detachBlockHint(pos: BlockPos, index: Int) {
    val hints = blockHints ?: return
    val texts = hints[pos]?.texts?.toMutableList() ?: return
    val newHints = hints.toMutableMap()
    texts.removeAt(index)
    if (texts.isEmpty()) {
        newHints.remove(pos)
    } else {
        newHints[pos] = hints[pos]!!.copy(texts = texts)
    }
}