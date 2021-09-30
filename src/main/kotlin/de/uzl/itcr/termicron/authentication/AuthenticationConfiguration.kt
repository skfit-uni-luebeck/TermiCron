package de.uzl.itcr.termicron.authentication

import java.net.URL
import java.net.URI

/**
 * abstract class for configrations for the various AuthDrivers
 *
 * @property authEndpoint the endpoint where authentication is carried out against
 */
abstract class AuthenticationConfiguration(
    private val authEndpoint: URL? = null
) {

    /**
     * resolve a path against the authEndpoint
     *
     * @param path the path to resolve
     * @return the resolved URL, with the path appended
     */
    fun buildAuthUrl(path: String): URI {
        return URI.create("${authEndpoint.toString().trimEnd('/')}/${path.trimStart('/')}")
    }
}

class NoOpAuthenticationConfiguration : AuthenticationConfiguration()
