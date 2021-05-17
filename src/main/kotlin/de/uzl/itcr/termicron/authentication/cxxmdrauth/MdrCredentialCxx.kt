package de.uzl.itcr.termicron.authentication.cxxmdrauth

import de.uzl.itcr.termicron.authentication.MdrCredential
import java.time.LocalDateTime

/**
 * a Kairos CentraXX MDR access token with specified lifetime
 *
 * @property accessToken the secret token
 * @property tokenType the type of the token, generally "Bearer"
 * @property scope the scope of the token, generally "anyscope"
 * @constructor
 * implaments a MDRCredential
 *
 * @param expiresSeconds the expiry in seconds, automatically converted to a LocalDateTime
 */
class MdrCredentialCxx(
    val accessToken: String,
    expiresSeconds: Int,
    private val tokenType: String,
    private val scope: String
) : MdrCredential {

    /**
     * the point in time where the credential was obtained at
     */
    private val obtainedAt: LocalDateTime = LocalDateTime.now()

    /**
     * the time the credential expires at
     */
    private val expiresAt: LocalDateTime = obtainedAt.plusSeconds(expiresSeconds.toLong())

    override fun isExpired(): Boolean {
        return LocalDateTime.now() > expiresAt
    }

    override fun encodeCredentialToAuthorizationHeader(): String {
        return String.format("%s %s", tokenType, accessToken)
    }
}