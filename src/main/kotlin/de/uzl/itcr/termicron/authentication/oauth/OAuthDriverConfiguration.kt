package de.uzl.itcr.termicron.authentication.oauth

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import java.net.URL

/**
 * configuration for authentication against OAuth2 secured APIs, using the "public" flow
 *
 * @property clientId the client ID
 * @property clientSecret the client secret
 * @property callbackDomain the callback domain the embedded webserver listens on. Genere
 * @property callbackPort the port the callback listens on.
 * @property callbackPath the path the callback listens on
 * @constructor
 * implements an AuthenticationConfiguration
 *
 * @param authEndpoint the endpoint where authentication is carried out against
 */
class OAuthDriverConfiguration(
    authEndpoint: URL,
    val clientId: String,
    val clientSecret: String,
    val callbackDomain: String = defaultCallbackDomain,
    val callbackPort: Int = defaultCallbackPort,
    val callbackPath: String = defaultCallbackPath
) : AuthenticationConfiguration(authEndpoint) {
    companion object {
        /**
         * the default domain of the callback
         */
        const val defaultCallbackDomain = "127.0.0.1"

        /**
         * the default port of the callback
         */
        const val defaultCallbackPort = 8081

        /**
         * the default path of the callback
         */
        const val defaultCallbackPath = "/"
    }
}