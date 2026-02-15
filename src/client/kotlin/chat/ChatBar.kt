package org.lain.engine.client.chat

import org.lain.engine.chat.ChannelId

fun ChatBar(configuration: ChatBarConfiguration) = ChatBar(configuration.sections)

class ChatBar(val sections: List<ChatBarSection>) {
    private val states: MutableMap<ChannelId, ChatBarSectionState> = mutableMapOf()
    private val statesBySection: MutableMap<ChatBarSection, ChatBarSectionState> = mutableMapOf()

    fun copy(chat: ClientEngineChatManager?, configuration: ChatBarConfiguration): ChatBar {
        return ChatBar(configuration).also { bar ->
            states
                .forEach { (channelId, section) ->
                    if (section.hide) bar.toggleHide(channelId, chat)
                    if (section.unread) bar.markUnread(channelId)
                }
        }
    }

    init {
        sections.forEach { section ->
            val state = ChatBarSectionState(section, hide = false, unread = false, mentioned = false)
            section.channels.forEach {
                states[it] = state
            }
            statesBySection[section] = state
        }
    }

    fun isHidden(channelId: ChannelId) = states[channelId]?.let { isHidden(it.section) } ?: false

    fun isHidden(section: ChatBarSection) = statesBySection[section]?.hide ?: false

    fun toggleHide(channelId: ChannelId, chat: ClientEngineChatManager?) {
        states[channelId]?.let { toggleHide(it.section, chat)}
    }

    fun toggleHide(section: ChatBarSection, chat: ClientEngineChatManager?) {
        val channelState = statesBySection[section] ?: return
        channelState.hide = !channelState.hide
        val isHidden = channelState.hide
        if (isHidden) {
            section.channels.forEach { chat?.disableChannel(it) }
        } else {
            markRead(section)
            section.channels.forEach { chat?.enableChannel(it) }
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