package org.lain.engine.player.account

import kotlinx.serialization.Serializable

@Serializable
data class AccountResponse(
    val id: String,
    val nickname: String,
    val registeredAt: String,
    val characters: List<CharacterDataResponse> = emptyList(),
)

