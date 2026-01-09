package org.lain.engine.util.text

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.text.OrderedText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.text.TextCodecs
import net.minecraft.util.Colors
import org.lain.engine.util.Color
import org.lain.engine.util.parseHexColor

data class EngineTextShadow(val color: Color?, val enabled: Boolean)

data class EngineText(
    val content: String,
    val style: EngineTextStyle = EmptyEngineTextStyle(),
    val siblings: List<EngineText> = listOf()
)

fun splitEngineText(node: EngineText, by: String): List<EngineText> {
    val newChildren = node.siblings.flatMap { splitEngineText(it, by) }

    if (newChildren.isNotEmpty()) {
        return listOf(node.copy(siblings = newChildren))
    }

    return node.content
        .split(by)
        .filter { it.isNotEmpty() }
        .map { part ->
            node.copy(
                content = part,
                siblings = emptyList()
            )
        }
}

fun splitEngineTextLinear(node: EngineText, by: String): List<EngineOrderedText> {
    val output = mutableListOf<EngineOrderedText>()
    visitEngineText(node) { text ->
        val split = text.content.split(by)
        output.addAll(split.mapIndexed { index, it ->
            val content = mutableListOf(it)
            if (index != split.size) { content.add(by) }
            text.deepCopy(content = content.joinToString()) }
        )
    }
    return output
}

fun resolveText(ordered: MutableEngineOrderedText, text: EngineText) {
    val style = text.style
    val orderedStyle = ordered.style
    ordered.content = text.content
    style.color?.let { orderedStyle.color = it }
    style.strike?.let { orderedStyle.strike = it }
    style.underline?.let { orderedStyle.underline = it }
    style.italic?.let { orderedStyle.italic = it }
    style.obfuscated?.let { orderedStyle.obfuscated = it }
    style.bold?.let { orderedStyle.bold = it }
    style.shadow?.let { orderedStyle.shadowColor = if (it.enabled) it.color else null }
}

fun visitEngineText(
    node: EngineText,
    ordered: MutableEngineOrderedText = MutableEngineOrderedText(),
    visitor: (EngineOrderedText) -> Unit
) {
    val snapshot = ordered.copy()

    resolveText(ordered, node)
    visitor(ordered)

    node.siblings.forEach { child ->
        visitEngineText(child, ordered, visitor)
    }

    ordered.content = snapshot.content
    ordered.style.color = snapshot.style.color
    ordered.style.bold = snapshot.style.bold
    ordered.style.underline = snapshot.style.underline
    ordered.style.italic = snapshot.style.italic
    ordered.style.strike = snapshot.style.strike
    ordered.style.obfuscated = snapshot.style.obfuscated
    ordered.style.shadow = snapshot.style.shadow
}