package org.lain.engine.chat

@JvmInline
value class ChatPipelineId(val value: String) {
    override fun toString(): String = value
}