package org.lain.engine.util.text

import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.TextColor
import org.lain.engine.mixin.StyleAccessor
import org.lain.engine.player.CustomName
import org.lain.engine.player.DisplayName
import org.lain.engine.player.EnginePlayer
import org.lain.engine.util.Color
import org.lain.engine.util.require
import java.util.Optional

val EnginePlayer.displayNameMiniMessage
    get() = this.require<DisplayName>().let { it.custom?.textMiniMessage ?: it.username.value }

private val CustomName.textMiniMessage
    get() = "<gradient:#${hex(color1)}:#${hex(color2 ?: color1)}>$string</gradient>"

private fun hex(color: Color): String {
    return "%06x".format(color.integer and 0xFFFFFF)
}

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
    style.color?.baseColor,
    style.shadow?.color,
    style.bold,
    style.italic,
    style.underline,
    style.strike,
    style.obfuscated
)

fun EngineTextSpan.toMinecraft(): OrderedText {
    return EngineOrderedText(listOf(this)).toMinecraft()
}

fun EngineOrderedText.toMinecraft(): OrderedText {
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
