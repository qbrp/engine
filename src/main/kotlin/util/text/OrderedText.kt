package org.lain.engine.util.text

interface EngineOrderedText {
    val content: String
    val style: OrderedEngineTextStyle

    fun deepCopy(content: String = this.content): EngineOrderedText
}

data class MutableEngineOrderedText(
    override var content: String = "",
    override val style: OrderedEngineTextStyle = OrderedEngineTextStyle()
) : EngineOrderedText {
    override fun deepCopy(content: String): EngineOrderedText {
        return this.copy(content = content)
    }
}

class EngineOrderedTextSequence(val parts: List<EngineOrderedText>) : List<EngineOrderedText> by parts