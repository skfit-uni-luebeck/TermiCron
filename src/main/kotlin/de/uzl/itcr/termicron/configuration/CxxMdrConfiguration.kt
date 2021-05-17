package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.cxxmdrauth.CxxMdrAuthConfiguration
import java.net.URL

/**
 * configuration for a CentraXX MDR
 *
 * @property authenticationConfiguration the authentication configuration
 * @constructor
 * instantiates a MdrConfiguration
 *
 * @param apiEndpoint the endpoint of the CentraXX MDR api
 */
class CxxMdrConfiguration(
    val authenticationConfiguration: CxxMdrAuthConfiguration,
    apiEndpoint: URL
) : MdrConfiguration(apiEndpoint)