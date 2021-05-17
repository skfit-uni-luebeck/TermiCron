package de.uzl.itcr.termicron.authentication.oauth

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties
import java.net.URL

class OAuthDriverConfiguration(
    authEndpoint: URL,
    val clientId: String,
    val clientSecret: String,
    val callbackDomain: String = "127.0.0.1",
    val callbackPort: Int = 8080
) : AuthenticationConfiguration(authEndpoint)