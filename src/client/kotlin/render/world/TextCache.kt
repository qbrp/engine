package org.lain.engine.client.render.world

import net.minecraft.network.chat.FormattedText
import org.lain.engine.client.mc.parseMiniMessageClient

class TextCache(
    private val maxSize: Int = 1000,
) {
    private val values = object : LinkedHashMap<String, FormattedText>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FormattedText>): Boolean {
            return size > maxSize
        }
    }

    fun get(text: String): FormattedText = values.getOrPut(text) {
        text.parseMiniMessageClient()
    }
}
