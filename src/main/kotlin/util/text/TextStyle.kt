package org.lain.engine.util.text

import org.lain.engine.util.Color

interface EngineTextStyle {
    val color: Color?
    val bold: Boolean?
    val underline: Boolean?
    val italic: Boolean?
    val strike: Boolean?
    val obfuscated: Boolean?
    val shadow: EngineTextShadow?
}

fun EmptyEngineTextStyle(): EngineTextStyle = MutableEngineTextStyle()

fun EngineTextStyle(
    color: Color? = null,
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
    override var color: Color? = null,
    override var bold: Boolean? = null,
    override var underline: Boolean? = null,
    override var italic: Boolean? = null,
    override var strike: Boolean? = null,
    override var obfuscated: Boolean? = null,
    override var shadow: EngineTextShadow? = null,
) : EngineTextStyle

data class OrderedEngineTextStyle(
    override var color: Color = Color.WHITE,
    override var bold: Boolean = false,
    override var underline: Boolean = false,
    override var italic: Boolean = false,
    override var strike: Boolean = false,
    override var obfuscated: Boolean = false,
    var shadowColor: Color? = null
) : EngineTextStyle {
    override var shadow: EngineTextShadow = EngineTextShadow(shadowColor, false)
}

