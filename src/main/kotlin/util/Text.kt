package org.lain.engine.util

import com.google.gson.JsonParser
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.registry.DynamicRegistryManager
import net.minecraft.registry.RegistryWrapper
import net.minecraft.text.Style
import net.minecraft.text.Text
import kotlin.collections.plus

fun String.parseMiniMessage(
    wrapperLookup: RegistryWrapper.WrapperLookup = DynamicRegistryManager.EMPTY
): Text {
    val text = this
        .replace("§", "&")
        .replace("&0", "<black>")
        .replace("&1", "<dark_blue>")
        .replace("&2", "<dark_green>")
        .replace("&3", "<dark_aqua>")
        .replace("&4", "<dark_red>")
        .replace("&5", "<dark_purple>")
        .replace("&6", "<gold>")
        .replace("&7", "<gray>")
        .replace("&8", "<dark_gray>")
        .replace("&9", "<blue>")
        .replace("&a", "<green>")
        .replace("&b", "<aqua>")
        .replace("&c", "<red>")
        .replace("&d", "<light_purple>")
        .replace("&e", "<yellow>")
        .replace("&f", "<white>")
        .replace("&r", "<resetCustom>")
        .replace("&l", "<bold>")
        .replace("&o", "<italic>")

    val component = MiniMessage.miniMessage().deserialize(text)
    val jsonObject = JsonParser.parseString(GsonComponentSerializer.gson().serialize(component)).deepCopy()

    return Text.Serialization.fromJsonTree(jsonObject, wrapperLookup)!!
}

fun main() {
    println(
        transformStringTextLines(
        parseLinearTextNode("<red><blue>test input <bold>дополнительное форматирование <purple>еще форматирование")
        )
            .joinToString(separator = "\n")
    )
}

data class Tag(
    val name: String,
    val args: List<String>,
)

data class StringTextNode(
    val text: String? = null,
    val tags: List<Tag> = listOf(),
)

/**
 * Парсит текст в невложенный линейный набор StringTextNode-ов, независимых друг от друга
 */
fun parseLinearTextNode(input: String): List<StringTextNode> {
    val currentTags = mutableListOf<Tag>()
    val nodes = mutableListOf<StringTextNode>()

    var i = 0
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            nodes += StringTextNode(
                text = textBuffer.toString(),
                tags = currentTags.toList(),
            )
            textBuffer.clear()
        }
    }

    while (i < input.length) {
        if (input[i] == '<') {
            val end = input.indexOf('>', i)
            if (end == -1) break

            val rawTag = input.substring(i + 1, end).trim()
            flushText()

            if (rawTag.startsWith("/")) {
                val tagName = rawTag.drop(1)
                currentTags.removeAll { it.name == tagName }
            } else {
                val parts = rawTag.split(':')
                val tag = Tag(parts[0], parts.drop(1))
                currentTags += tag
            }

            i = end + 1
        } else {
            textBuffer.append(input[i])
            i++
        }
    }

    flushText()

    return nodes
}

private fun <K : Any, V : Any> Map<List<K>, V>.flattern(): Map<K, V> {
    val output = mutableMapOf<K, V>()
    forEach { (list, value) ->
        list.forEach { elem ->
            output[elem] = value
        }
    }
    return output
}

private val COLOR_TAGS = mapOf(
    listOf("black", "0") to 0x000000,
    listOf("dark_blue", "1") to 0x0000AA,
    listOf("dark_green", "2") to 0x00AA00,
    listOf("dark_aqua", "3") to 0x00AAAA,
    listOf("dark_red", "4") to 0xAA0000,
    listOf("dark_purple", "5") to 0xAA00AA,
    listOf("gold", "6") to 0xFFAA00,
    listOf("gray", "7") to 0xAAAAAA,
    listOf("dark_gray", "8") to 0x555555,
    listOf("blue", "9") to 0x5555FF,
    listOf("green", "a") to 0x55FF55,
    listOf("aqua", "b") to 0x55FFFF,
    listOf("red", "c") to 0xFF5555,
    listOf("light_purple", "d") to 0xFF55FF,
    listOf("yellow", "e") to 0xFFFF55,
    listOf("white", "f") to 0xFFFFFF
).flattern()

fun transformStringTextLines(lines: List<StringTextNode>): List<TextNode> {
    val transformed = mutableListOf<TextNode>()
    for (line in lines) {
        val tags = line.tags
        val attributes = mutableMapOf<TextNode.AttributeType, TextNode.Attribute>()
        
        for (tag in tags) {
            val attribute = TextNode.attribute(tag)
            if (attribute != null) {
                attributes[attribute.type] = attribute
            }
        }

        transformed += TextNode(
            line.text,
            attributes.values.toList()
        )
    }
    return transformed
}

data class TextNode(
    val content: String? = null,
    val attributes: List<Attribute>
) {
    enum class AttributeType {
        COLOR, BOLD, STRIKE, UNDERLINE, OBFUSCATED, ITALIC
    }

    sealed class Attribute(val type: AttributeType) {
        class Color(val color: Int) : Attribute(AttributeType.COLOR)
        class Enable(type: AttributeType) : Attribute(type)
    }

    companion object {
        fun attribute(tag: Tag): Attribute? {
            val name = tag.name
            return if (name == "italic" || name == "i") {
                Attribute.Enable(AttributeType.ITALIC)
            } else if (name == "bold" || name == "b") {
                Attribute.Enable(AttributeType.BOLD)
            } else if (name == "strike" || name == "strikethrough" || name == "st") {
                Attribute.Enable(AttributeType.STRIKE)
            } else if (name == "underline" || name == "u") {
                Attribute.Enable(AttributeType.UNDERLINE)
            } else if (name == "obfuscated" || name == "cursed" || name == "obf") {
                Attribute.Enable(AttributeType.OBFUSCATED)
            } else if (name.startsWith("#") && name.length == 7) {
                Attribute.Color(parseHexColor(name))
            } else {
                val color = COLOR_TAGS[name] ?: return null
                Attribute.Color(color)
            }
        }
    }
}

fun EngineText.toMinecraft(): Text {
    val text = Text.literal(
        content
    ).styled {
        var style = Style.EMPTY
        this.style.color?.let { style = style.withColor(it) }
        this.style.bold?.let { style = style.withBold(it) }
        this.style.underline?.let { style = style.withUnderline(it) }
        this.style.italic?.let { style = style.withItalic(it) }
        this.style.strike?.let { style = style.withStrikethrough(it) }
        this.style.obfuscated?.let { style = style.withObfuscated(it) }
        this.style.shadow?.let { style = style.withShadowColor(it) }
        style
    }
    for (sibling in siblings) {
        text.append(sibling.toMinecraft())
    }
    return text
}

data class EngineTextStyle(
    val color: Int? = null,
    val scale: Float? = null,
    val bold: Boolean? = null,
    val underline: Boolean? = null,
    val italic: Boolean? = null,
    val strike: Boolean? = null,
    val obfuscated: Boolean? = null,
    val shadow: Int? = null,
)

data class EngineText(
    val content: String,
    val style: EngineTextStyle,
    val siblings: List<EngineText>
)