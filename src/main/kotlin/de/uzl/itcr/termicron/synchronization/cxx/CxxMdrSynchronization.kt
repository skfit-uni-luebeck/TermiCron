package de.uzl.itcr.termicron.synchronization.cxx

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver
import de.uzl.itcr.termicron.authentication.addAuthorizationHeader
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.configuration.CxxMdrConfiguration
import de.uzl.itcr.termicron.output.cxx.CxxMdrOutputRest
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * implement CentraXX MDR synchronization via the REST api
 *
 * @property authDriver the authentication driver for CentraXX MDR
 * @property mdrConfiguration the configuration of the system
 * @property outputRest the output provider
 */
class CxxMdrSynchronization(
    private val authDriver: MdrAuthenticationDriver,
    private val mdrConfiguration: CxxMdrConfiguration,
    private val outputRest: CxxMdrOutputRest
) : MdrSynchronization() {

    /**
     * the logger for this class
     */
    private val log = LoggerFactory.getLogger(CxxMdrSynchronization::class.java)

    /**
     * the HttpClient instance for this class
     */
    private val client: HttpClient = StaticHelpers.httpClient()

    /**
     * check if the VS is present, by requesting /catalogs/catalog by code and version
     *
     * @param vs the VS
     * @return true if the VS is present
     */
    override fun isPresent(vs: ValueSetExpansion): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl("/catalogs/catalog?code=${vs.nameUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))

            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return (HttpStatus.valueOf(response.statusCode()).is2xxSuccessful).also { log.debug("expanded VS '${vs.title} @ version '${vs.businessVersion}' is ${if (it) "present" else "not present"}") }
    }

    /**
     * check if the VS is current, by requesting catalogs/catalog by code and version
     *
     * @param vs the ValueSet to check
     * @return true if a ValueSet of this version is present
     */
    override fun isCurrent(vs: ValueSetExpansion): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl("catalogs/catalog?code=${vs.nameUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))
            .addAuthorizationHeader(authDriver)
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return (HttpStatus.valueOf(response.statusCode()).is2xxSuccessful).also { log.debug("expanded VS '${vs.title} @ version '${vs.businessVersion}' is ${if (it) "current" else "not current"}") }
    }

    /**
     * create a ValueSet in this MDR
     *
     * @param vs the VS to create
     * @return true if the VS could be successfully created
     */
    override fun create(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return if (convertedValueSet.success) {
            val request = HttpRequest.newBuilder()
                .uri(mdrConfiguration.buildApiUrl("catalogs/catalog"))
                .header("Content-Type", "application/json;charset=UTF-8")
                .addAuthorizationHeader(authDriver)
                .POST(HttpRequest.BodyPublishers.ofString(convertedValueSet.result))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            (HttpStatus.valueOf(response.statusCode()).is2xxSuccessful).also {
                log.debug("expanded VS '${vs.title} @ version '${vs.businessVersion}' was ${if (it) "successfully" else "not successfully"} created")
            }
        } else {
            false
        }
    }

    /**
     * update a VS in the MDR
     *
     * @param vs the VS to update
     * @return true if the VS could be successfully updated
     */
    override fun update(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return if (convertedValueSet.success) {
            val request = HttpRequest.newBuilder()
                .uri(mdrConfiguration.buildApiUrl("catalogs/catalog?code=${vs.titleUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))
                .header("Content-Type", "application/json;charset=UTF-8")
                .addAuthorizationHeader(authDriver)
                .PUT(HttpRequest.BodyPublishers.ofString(convertedValueSet.result))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            (HttpStatus.valueOf(response.statusCode()).is2xxSuccessful).also {
                log.debug("expanded VS '${vs.title} @ version '${vs.businessVersion}' was ${if (it) "successfully" else "not successfully"} updated")
            }
        } else {
            false
        }
    }

    override fun validateEndpoint(): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl("/"))
            .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.mimeType)
            .addAuthorizationHeader(authDriver)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        when (val statusCode = HttpStatus.valueOf(response.statusCode())) {
            HttpStatus.OK -> return true
            HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN -> log.error("Unauthorized - check your credentials")
            else -> log.error(
                "Error communicating with CentraXX MDR. " +
                        "Got HTTP status code $statusCode via URL ${mdrConfiguration.apiEndpoint}"
            )
        }
        return false
    }
}