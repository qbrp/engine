package org.lain.engine.util.text

interface EngineTextSpan {
    val content: String
    val style: OrderedEngineTextStyle

    fun deepCopy(content: String = this.content): EngineTextSpan
}

data class MutableEngineOrderedText(
    override var content: String = "",
    override val style: OrderedEngineTextStyle = OrderedEngineTextStyle()
) : EngineTextSpan {
    override fun deepCopy(content: String): EngineTextSpan {
        return this.copy(content = content)
    }
}

class EngineOrderedText(val parts: List<EngineTextSpan>) : List<EngineTextSpan> by parts