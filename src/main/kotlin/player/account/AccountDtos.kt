package org.lain.engine.player.account

import kotlinx.serialization.Serializable

@Serializable
data class CreateAccountRequest(
    val nickname: String,
)

@Serializable
data class UpdateAccountRequest(
    val nickname: String? = null,
)

@Serializable
data class AccountResponse(
    val id: String,
    val nickname: String,
    val registeredAt: String,
    val characters: List<CharacterData> = emptyList(),
)

