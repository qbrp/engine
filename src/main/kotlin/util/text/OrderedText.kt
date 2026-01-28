package org.lain.engine.util.text

interface EngineTextSpan {
    val content: String
    val style: OrderedEngineTextStyle

    fun deepCopy(content: String = this.content): EngineTextSpan
}

data class MutableEngineTextSpan(
    override var content: String = "",
    override val style: OrderedEngineTextStyle = OrderedEngineTextStyle()
) : EngineTextSpan {
    override fun deepCopy(content: String): EngineTextSpan {
        return this.copy(content = content)
    }
}

class EngineOrderedText(val parts: List<EngineTextSpan>) : List<EngineTextSpan> by parts {
    fun with(text: EngineText): EngineOrderedText {
        val parts = parts.toMutableList()
        visitEngineText(text) { span -> parts.add(span) }
        return EngineOrderedText(parts)
    }
}