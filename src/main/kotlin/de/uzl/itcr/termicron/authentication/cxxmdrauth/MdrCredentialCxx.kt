package de.uzl.itcr.termicron.authentication.cxxmdrauth

import de.uzl.itcr.termicron.authentication.MdrCredential
import java.time.LocalDateTime

class MdrCredentialCxx(
    val accessToken: String,
    expiresSeconds: Int,
    private val tokenType: String,
    private val scope: String
) : MdrCredential {
    private val obtainedAt: LocalDateTime = LocalDateTime.now()
    private val expiresAt: LocalDateTime = obtainedAt.plusSeconds(expiresSeconds.toLong())

    override fun isExpired(): Boolean {
        return LocalDateTime.now() > expiresAt
    }

    override fun encodeCredentialToAuthorizationHeader(): String {
        return String.format("%s %s", tokenType, accessToken)
    }
}