package de.uzl.itcr.termicron.authentication

import java.net.URL
import java.net.URI

abstract class AuthenticationConfiguration(
    private val authEndpoint: URL
) {
    fun buildAuthUrl(path: String): URI {
        return URI.create("${authEndpoint.toString().trimEnd('/')}/${path.trimStart('/')}")
    }
}
