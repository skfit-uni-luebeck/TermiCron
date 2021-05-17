package de.uzl.itcr.termicron.authentication.cxxmdrauth

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import de.uzl.itcr.termicron.authentication.MdrCredentialBasicAuth
import java.net.URL

/**
 * represent the authentication configuration for a Kairos CentraXX MDR instance
 *
 * @property userName the username to use for accessing the API
 * @property password the password of that user
 * @property clientId the client ID to use for authentication
 * @property clientSecret the client secret to provide for authentication*
 * @param authEndpoint the endpoint to authenticate against
 */
class CxxMdrAuthConfiguration(
    authEndpoint: URL,
    val userName: String,
    val password: String,
    private val clientId: String,
    private val clientSecret: String
) : AuthenticationConfiguration(authEndpoint)
{
    /**
     * the basic authentication credential identifying the client
     */
    val clientBasicAuth: MdrCredentialBasicAuth
        get() = MdrCredentialBasicAuth(clientId, clientSecret)
}