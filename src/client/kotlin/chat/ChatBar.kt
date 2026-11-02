package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.client.mc.chat.MinecraftChat.chatManager

fun ChatBar(configuration: ChatBarConfiguration, old: ChatBar? = null) = ChatBar(configuration.sections, old)

class ChatBar(
    val sections: List<ChatBarSection>,
    old: ChatBar? = null
) {
    private val states: MutableMap<ChannelId, ChatBarSectionState> = mutableMapOf()
    private val statesBySection: MutableMap<ChatBarSection, ChatBarSectionState> = mutableMapOf()

    init {
        val oldSections = old?.statesBySection ?: emptyMap()
        sections.forEach { section ->
            val oldState = oldSections[section]
            val state = ChatBarSectionState(
                section,
                hide = oldState?.hide ?: false,
                unread = oldState?.unread ?: false,
                mentioned = oldState?.mentioned ?: false
            )
            section.channels.forEach {
                states[it] = state
            }
            statesBySection[section] = state
        }
    }

    fun isHidden(channelId: ChannelId) = states[channelId]?.let { isHidden(it.section) } ?: false

    fun isHidden(section: ChatBarSection) = statesBySection[section]?.hide ?: false

    fun toggleHide(channelId: ChannelId) {
        states[channelId]?.let { toggleHide(it.section)}
    }

    fun toggleHide(section: ChatBarSection) {
        val channelState = statesBySection[section] ?: return
        channelState.hide = !channelState.hide
        if (!channelState.hide) markRead(section)
    }

    fun hasUnreadMessages(section: ChatBarSection): Boolean {
        return statesBySection[section]?.unread ?: false
    }

    fun wasMentioned(section: ChatBarSection): Boolean {
        return statesBySection[section]?.mentioned ?: false
    }

    fun markUnread(channel: ChannelId) {
        states[channel]?.unread = true
    }

    fun markRead(channel: ChannelId) {
        states[channel]?.let { markRead(it.section) }
    }

    fun markRead(section: ChatBarSection) {
        statesBySection[section]?.let {
            it.unread = false
            it.mentioned = false
        }
    }

    fun markMentioned(channel: ChannelId) {
        states[channel]?.mentioned = true
    }
}