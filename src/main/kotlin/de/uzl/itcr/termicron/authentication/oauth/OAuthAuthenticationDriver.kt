package de.uzl.itcr.termicron.authentication.oauth

import com.google.api.client.auth.oauth2.*
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver

class OAuthAuthenticationDriver(driverConfiguration: OAuthDriverConfiguration) : MdrAuthenticationDriver(
    driverConfiguration
) {

    private val credentialAccessMethod = BearerToken.authorizationHeaderAccessMethod()
    private val transport = NetHttpTransport()
    private val gsonFactory = GsonFactory()
    private val tokenUrl = GenericUrl(driverConfiguration.buildAuthUrl("token"))
    private val authUrl = driverConfiguration.buildAuthUrl("auth").toString()
    private val clientParametersAuthentication = ClientParametersAuthentication(driverConfiguration.clientId, driverConfiguration.clientSecret)

    private var requestedOauthAccessToken : Credential? = null

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

    override fun needLoginToMdr(): Boolean = this.mdrCredential?.isExpired() ?: true
}