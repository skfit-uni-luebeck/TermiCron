package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.cxxmdrauth.CxxMdrAuthConfiguration
import de.uzl.itcr.termicron.authentication.oauth.OAuthDriverConfiguration
import java.net.URL

class QL4MDRConfiguration(
    val authenticationConfiguration: OAuthDriverConfiguration,
    apiEndpoint: URL
) : MdrConfiguration(apiEndpoint) {
}