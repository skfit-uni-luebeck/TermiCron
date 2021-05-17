package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.oauth.OAuthDriverConfiguration
import java.net.URL

/**
 * a configuration for a QL2MDR-compliant MDR
 *
 * @property authenticationConfiguration the authentication configuration
 * @constructor
 * implements a MdrConfiguration
 *
 * @param apiEndpoint the QL4MDR endpoint
 */
class QL4MDRConfiguration(
    val authenticationConfiguration: OAuthDriverConfiguration,
    apiEndpoint: URL
) : MdrConfiguration(apiEndpoint) {
}