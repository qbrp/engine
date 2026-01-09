package org.lain.engine.util.text

import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextColor
import org.lain.engine.mixin.StyleAccessor
import org.lain.engine.util.Color
import java.util.Optional

private fun MinecraftStyle(
    color: Color?,
    shadow: Color?,
    bold: Boolean?,
    underline: Boolean?,
    italic: Boolean?,
    strike: Boolean?,
    obfuscated: Boolean?
): Style {
    return StyleAccessor.`engine$of`(
        Optional.ofNullable(color?.integer?.let { TextColor.fromRgb(it) }),
        Optional.ofNullable(shadow?.integer),
        Optional.ofNullable(bold),
        Optional.ofNullable(underline),
        Optional.ofNullable(italic),
        Optional.ofNullable(strike),
        Optional.ofNullable(obfuscated),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    )
}

private fun MinecraftStyle(style: EngineTextStyle) = MinecraftStyle(
    style.color,
    style.shadow?.color,
    style.bold,
    style.italic,
    style.underline,
    style.strike,
    style.obfuscated
)

fun EngineOrderedText.toMinecraft(): OrderedText {
    return EngineOrderedTextSequence(listOf(this)).toMinecraft()
}

fun EngineOrderedTextSequence.toMinecraft(): OrderedText {
    return OrderedText { visitor ->
        var globalIndex = 0

        for (node in this@toMinecraft) {
            val style = MinecraftStyle(node.style)

            var i = 0
            val text = node.content

            while (i < text.length) {
                val ch = text[i]
                if (!visitor.accept(globalIndex, style, ch.code)) {
                    return@OrderedText false
                }
                i++
                globalIndex++
            }
        }

        true
    }
}
