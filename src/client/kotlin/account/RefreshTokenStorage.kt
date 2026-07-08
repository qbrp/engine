package org.lain.engine.client.account

import com.microsoft.credentialstorage.SecretStore
import com.microsoft.credentialstorage.StorageProvider
import com.microsoft.credentialstorage.model.StoredToken
import com.microsoft.credentialstorage.model.StoredTokenType
import java.util.Arrays
import java.util.UUID

object RefreshTokenStorage {
    private val store: SecretStore<StoredToken> =
        StorageProvider.getTokenStorage(
            true,
            StorageProvider.SecureOption.REQUIRED
        )

    fun save(refreshToken: String): Boolean {
        val tokenChars = refreshToken.toCharArray()
        val token = StoredToken(tokenChars, StoredTokenType.REFRESH)

        return try {
            store.add("engine:refresh_token", token)
        } finally {
            token.clear()
            Arrays.fill(tokenChars, '\u0000')
        }
    }

    fun load(): RefreshToken? {
        val token = store.get("engine:refresh_token") ?: return null
        val value = token.value ?: return null

        return try {
            object : RefreshToken {
                private val tokenString = String(value)
                override fun get(): String {
                    return tokenString
                }
            }
        } finally {
            token.clear()
            Arrays.fill(value, '\u0000')
        }
    }

    fun delete(): Boolean {
        return store.delete("engine:refresh_token")
    }
}