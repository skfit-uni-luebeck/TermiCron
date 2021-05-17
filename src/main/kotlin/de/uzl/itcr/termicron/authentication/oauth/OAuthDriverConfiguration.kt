package de.uzl.itcr.termicron.authentication.oauth

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import java.net.URL

/**
 * configuration for authentication against OAuth2 secured APIs, using the "public" flow
 *
 * @property clientId the client ID
 * @property clientSecret the client secret
 * @property callbackDomain the callback domain the embedded webserver listens on. Genere
 * @property callbackPort the port the callback listens on.
 * @constructor
 * implements an AuthenticationConfiguration
 *
 * @param authEndpoint the endpoint where authentication is carried out against
 */
class OAuthDriverConfiguration(
    authEndpoint: URL,
    val clientId: String,
    val clientSecret: String,
    val callbackDomain: String = "127.0.0.1",
    val callbackPort: Int = 8080
) : AuthenticationConfiguration(authEndpoint)