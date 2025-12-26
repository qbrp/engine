package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId
import org.lain.engine.chat.EngineChat

class ChatBar(
    val configuration: ChatBarConfiguration,
    private val chat: ClientEngineChatManager,
    private val states: MutableMap<ChannelId, ChatBarSectionState> = mutableMapOf(),
    private val statesBySection: MutableMap<ChatBarSection, ChatBarSectionState> = mutableMapOf(),
) {
    init {
        configuration.sections.forEach { section ->
            val state = ChatBarSectionState(section, hide = false, unread = false, mentioned = false)
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
        val isHidden = channelState.hide
        if (isHidden) {
            markRead(section)
            section.channels.forEach { chat.disableChannel(it) }
        } else {
            section.channels.forEach { chat.enableChannel(it) }
        }
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