package de.uzl.itcr.termicron.authentication.cxxmdrauth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver
import org.apache.commons.logging.LogFactory
import org.apache.http.HttpStatus
import java.io.IOException
import java.net.URLEncoder
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset

/**
 * a driver for authenticating against the Kairos CentraXX MDR
 *
 * @constructor a MdrAuthenticationDriver instance
 *
 * @param cxxMdrAuthConfiguration the authentication configuration
 */
class CxxMdrAuthenticationDriver(cxxMdrAuthConfiguration: CxxMdrAuthConfiguration) :
    MdrAuthenticationDriver(cxxMdrAuthConfiguration) {
    private val httpClient = StaticHelpers.httpClient()
    private val loginRequest: HttpRequest

    private val log = LogFactory.getLog(CxxMdrAuthenticationDriver::class.java)

    @Throws(IOException::class, InterruptedException::class)  // TODO
    override fun loginToMdr() {
        val response = httpClient.send(loginRequest, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() == HttpStatus.SC_OK) {
            this.mdrCredential =
                Gson().fromJson(response.body().reader(), CxxMdrReceivedCredential::class.java).toCxxMdrCredential()
        } else {
            log.error("CXX responded with ${response.statusCode()} when requesting authentication!: ")
            val bytes = response.body().readAllBytes()
            log.error(String(bytes, Charset.defaultCharset()))
        }

    }

    override fun needLoginToMdr(): Boolean {
        return mdrCredential?.isExpired() ?: true
    }

    init {
        val dataMap = mapOf(
            "grant_type" to "password",
            "scope" to "anyscope",
            "username" to cxxMdrAuthConfiguration.userName,
            "password" to cxxMdrAuthConfiguration.password
        )
        loginRequest = HttpRequest.newBuilder()
            .uri(cxxMdrAuthConfiguration.buildAuthUrl("oauth/token"))
            .header("Authorization", cxxMdrAuthConfiguration.clientBasicAuth.encodeCredentialToAuthorizationHeader())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(getFormBody(dataMap)))
            .build()
    }

    private fun getFormBody(dataMap: Map<String, String>): String {
        val result = StringBuilder()
        var first = true
        for (entry in dataMap) {
            if (first) {
                first = false
            } else {
                result.append("&")
            }
            result.append(URLEncoder.encode(entry.key, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode(entry.value, "UTF-8"))
        }
        return result.toString()
    }

    data class CxxMdrReceivedCredential(
        @SerializedName("access_token") val accessToken: String,
        @SerializedName("expires_in") val expiresInSeconds: Long,
        @SerializedName("token_type") val tokenType: String,
        @SerializedName("scope") val scope: String
    ) {
        fun toCxxMdrCredential(): MdrCredentialCxx =
            MdrCredentialCxx(
                accessToken = accessToken,
                expiresSeconds = expiresInSeconds.toInt(),
                tokenType,
                scope
            )
    }
}