package de.uzl.itcr.termicron.configuration

import java.net.URI
import java.net.URL

abstract class MdrConfiguration(
    private val apiEndpoint: URL
) {

    fun buildApiUrl(path: String): URI {
        return URI.create("${apiEndpoint.toString().trimEnd('/')}/${path.trimStart('/')}")
    }

}
