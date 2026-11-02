package org.lain.engine.client.chat

import net.minecraft.util.FormattedCharSequence

fun FormattedCharSequence.countSymbols(): Int {
    var count = 0
    accept { _, _, _ ->
        count++
        true
    }
    return count
}
