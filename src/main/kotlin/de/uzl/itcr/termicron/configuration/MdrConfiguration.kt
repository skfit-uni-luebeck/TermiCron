package de.uzl.itcr.termicron.configuration

import java.net.URI
import java.net.URL

/**
 * an abstract MDR configuration
 *
 * @property apiEndpoint the endpoint of the MDR API
 */
abstract class MdrConfiguration(
    private val apiEndpoint: URL
) {

    /**
     * resolve a path against the MDR API url
     *
     * @param path the path to resolve
     * @return the resolved URI
     */
    fun buildApiUrl(path: String): URI {
        return URI.create("${apiEndpoint.toString().trimEnd('/')}/${path.trimStart('/')}")
    }

}
