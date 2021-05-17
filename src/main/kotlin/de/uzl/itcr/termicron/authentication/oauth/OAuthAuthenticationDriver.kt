package de.uzl.itcr.termicron.authentication.oauth

import com.google.api.client.auth.oauth2.*
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver

/**
 * Driver for authentication against OAuth2-secured APIs
 *
 * @constructor
 * implements a MdrAuthenticationDriver
 *
 * @param driverConfiguration the driver configuration
 */
class OAuthAuthenticationDriver(driverConfiguration: OAuthDriverConfiguration) : MdrAuthenticationDriver(
    driverConfiguration
) {

    /**
     * authentication against the authorization endpoint is using basic auth in the Authorization header
     */
    private val credentialAccessMethod = BearerToken.authorizationHeaderAccessMethod()

    /**
     * the transport for the Google OAuth library
     */
    private val transport = NetHttpTransport()

    /**
     * the GSON instance for the Google OAuth library
     */
    private val gsonFactory = GsonFactory()

    /**
     * the URL where tokens are available
     */
    private val tokenUrl = GenericUrl(driverConfiguration.buildAuthUrl("token"))

    /**
     * the URL where authorization codes are available
     */
    private val authUrl = driverConfiguration.buildAuthUrl("auth").toString()

    /**
     * authorize using provided client ID/client secret
     */
    private val clientParametersAuthentication = ClientParametersAuthentication(driverConfiguration.clientId, driverConfiguration.clientSecret)

    /**
     * the secret credential, in "raw" form. Used for re-issuing of tokens
     */
    private var requestedOauthAccessToken : Credential? = null

    /**
     * login to the MDR if required. This calls the "refresh" method of existing tokens, if applicable,
     * and starts a browser if no token is there yet. The user has to login to the browser, and an access code
     * is supplied to the authorization callback on the user's machine.
     * This method starts a short-lived webserver for the callback at the port conf.callbackPort to
     * listen for the access code.
     */
    override fun loginToMdr() {
        val conf = authenticationConfiguration as OAuthDriverConfiguration
        when (this.requestedOauthAccessToken) {
            null -> {
                val codeFlow = AuthorizationCodeFlow.Builder(
                    credentialAccessMethod,
                    transport,
                    gsonFactory,
                    tokenUrl,
                    clientParametersAuthentication,
                    conf.clientId,
                    authUrl
                )
                    .setScopes(listOf("openid"))
                    .build()
                val receiver = LocalServerReceiver.Builder()
                    .setHost(conf.callbackDomain)
                    .setPort(conf.callbackPort)
                    .build()
                requestedOauthAccessToken = AuthorizationCodeInstalledApp(codeFlow, receiver).authorize(null)
            }
            else -> {
                this.requestedOauthAccessToken!!.refreshToken()
            }
        }
        this.mdrCredential = MdrCredentialOAuth(
            this.requestedOauthAccessToken!!.accessToken,
            this.requestedOauthAccessToken!!.expiresInSeconds
        )
    }

    /**
     * check if the token is present and/or expired
     *
     * @return true if the token is not present, or if it is expired
     */
    override fun needLoginToMdr(): Boolean = this.mdrCredential?.isExpired() ?: true
}