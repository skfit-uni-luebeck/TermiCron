package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.cxxmdrauth.CxxMdrAuthConfiguration
import java.net.URL

class CxxMdrConfiguration(
    val authenticationConfiguration: CxxMdrAuthConfiguration,
    apiEndpoint: URL
) : MdrConfiguration(apiEndpoint)