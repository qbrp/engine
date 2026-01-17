package org.lain.engine.util.text

import org.lain.engine.util.Color

sealed class TextColor {
    data class Single(val color: Color) : TextColor()
    data class Gradient(val color1: Color, val color2: Color) : TextColor()
}

val TextColor.baseColor
    get() = when(this) {
        is TextColor.Gradient -> this.color1
        is TextColor.Single -> this.color
    }

fun TextColor(color1: Color, color2: Color? = null) = color2?.let { TextColor.Gradient(color1, color2) } ?: TextColor.Single(color1)

interface EngineTextStyle {
    val color: TextColor?
    val bold: Boolean?
    val underline: Boolean?
    val italic: Boolean?
    val strike: Boolean?
    val obfuscated: Boolean?
    val shadow: EngineTextShadow?
}

fun EmptyEngineTextStyle(): EngineTextStyle = MutableEngineTextStyle()

fun EngineTextStyle(
    color: TextColor? = null,
    bold: Boolean? = null,
    underline: Boolean? = null,
    italic: Boolean? = null,
    strike: Boolean? = null,
    obfuscated: Boolean? = null,
): EngineTextStyle = MutableEngineTextStyle(
    color,
    bold,
    underline,
    italic,
    strike,
    obfuscated
)

data class MutableEngineTextStyle(
    override var color: TextColor? = null,
    override var bold: Boolean? = null,
    override var underline: Boolean? = null,
    override var italic: Boolean? = null,
    override var strike: Boolean? = null,
    override var obfuscated: Boolean? = null,
    override var shadow: EngineTextShadow? = null,
) : EngineTextStyle

data class OrderedEngineTextStyle(
    override var color: TextColor = TextColor(Color.WHITE),
    override var bold: Boolean = false,
    override var underline: Boolean = false,
    override var italic: Boolean = false,
    override var strike: Boolean = false,
    override var obfuscated: Boolean = false,
    var shadowColor: Color? = null
) : EngineTextStyle {
    override var shadow: EngineTextShadow = EngineTextShadow(shadowColor, false)
}

