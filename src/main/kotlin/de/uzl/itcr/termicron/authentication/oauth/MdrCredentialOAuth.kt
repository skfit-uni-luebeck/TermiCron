package de.uzl.itcr.termicron.authentication.oauth

import de.uzl.itcr.termicron.authentication.MdrCredential
import java.time.LocalDateTime

class MdrCredentialOAuth(
    private val accessToken: String,
    expiresSeconds: Long
) : MdrCredential {

    private val obtainedAt: LocalDateTime = LocalDateTime.now()
    private val expiresAt: LocalDateTime = obtainedAt.plusSeconds(expiresSeconds)

    override fun isExpired(): Boolean {
        return LocalDateTime.now() > expiresAt
    }

    override fun encodeCredentialToAuthorizationHeader(): String = "Bearer $accessToken"
}