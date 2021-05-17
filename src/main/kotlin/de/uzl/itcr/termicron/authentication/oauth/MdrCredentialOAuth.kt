package de.uzl.itcr.termicron.authentication.oauth

import de.uzl.itcr.termicron.authentication.MdrCredential
import java.time.LocalDateTime

/**
 * represent a OAuth2 credential
 *
 * @property accessToken the secret credential
 * @constructor
 * Implements an MdrCredential
 *
 * @param expiresSeconds the expiry in seconds, automatically converted to a LocalDateTime
 */
class MdrCredentialOAuth(
    private val accessToken: String,
    expiresSeconds: Long
) : MdrCredential {

    /**
     * the point in time where the credential was obtained at
     */
    private val obtainedAt: LocalDateTime = LocalDateTime.now()

    /**
     * the time the credential expires at
     */
    private val expiresAt: LocalDateTime = obtainedAt.plusSeconds(expiresSeconds)

    override fun isExpired(): Boolean {
        return LocalDateTime.now() > expiresAt
    }

    override fun encodeCredentialToAuthorizationHeader(): String = "Bearer $accessToken"
}