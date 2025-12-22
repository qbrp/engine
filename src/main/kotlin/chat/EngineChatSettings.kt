package org.lain.engine.chat

data class EngineChatSettings(
    val placeholders: Map<String, String> = mapOf(),
    val channels: List<ChatChannel> = listOf(),
    val distortionThreshold: Float = 0.3f,
    val distortionArtifacts: List<Char> = listOf(),
    val realisticAcousticFormatting: AcousticFormatting = AcousticFormatting(),
    val acousticHearingThreshold: Float = 0.05f,
    val acousticMaxVolume: Float = 1.5f,
    val defaultChannel: ChatChannel = BUILTIN_DEFAULT_CHANNEL,
    val joinMessage: String = BUILTIN_JOIN_MESSAGE,
    val joinMessageEnabled: Boolean = true,
    val leaveMessage: String = BUILTIN_LEAVE_MESSAGE,
    val leaveMessageEnabled: Boolean = true,
    val pmFormat: String = BUILTIN_FORMAT_PM,
) {
    companion object {
        val BUILTIN_DEFAULT_CHANNEL = ChatChannel(
            ChannelId("default"),
            "Разговор",
            "{author_name}: {text}",
            Acoustic.Distance(32),
            listOf(),
            listOf(),
            true,
            false
        )

        const val BUILTIN_FORMAT_PM = "{author_name} -> {recipient_name}: {text}"

        const val BUILTIN_JOIN_MESSAGE = "+ {author_name}"
        const val BUILTIN_LEAVE_MESSAGE = "- {author_name}"
    }
}