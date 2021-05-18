package de.uzl.itcr.termicron.synchronization.ql4mdr

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.configuration.QL4MDRConfiguration
import de.uzl.itcr.termicron.output.ql4mdr.QL4MDRMdrOutput
import de.uzl.itcr.termicron.output.ql4mdr.ql4mdr
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import ninja.sakib.jsonq.JSONQ
import org.apache.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * implement GraphQL/QL4MDR synchronization
 *
 * @property authDriver the authentication driver
 * @property mdrConfiguration the configuration of the QL4MDR-compliant MDR
 * @property outputRest the rest output driver
 */
class QL4MDRSynchronization(
    private val authDriver: MdrAuthenticationDriver,
    private val mdrConfiguration: QL4MDRConfiguration,
    private val outputRest: QL4MDRMdrOutput
) : MdrSynchronization() {

    /**
     * the logger for this class
     */
    val logger: Logger = LoggerFactory.getLogger(QL4MDRSynchronization::class.java)

    /**
     * the HttpClient for this class
     */
    private val client: HttpClient = StaticHelpers.httpClient()

    /**
     * write a query for the provided VS that checks the presence via GraphQL
     *
     * @param vs the ValueSet
     * @return the GraphQL DSL
     */
    private fun presenceQuery(vs: ValueSetExpansion) = ql4mdr {
        query {
            queryEntity("conceptSystem") {
                queryArguments {
                    +("uri" to vs.canonicalUrl)
                    +("version" to vs.businessVersion)
                }
                +"name"
            }
        }
    }

    /**
     * present == current
     *
     * @param vs the VS to use
     * @return true if present and current
     */
    override fun isPresent(vs: ValueSetExpansion): Boolean = sendPresentCurrentQuery(vs)

    /**
     * present == current
     *
     * @param vs the vs to use
     * @return true if present and current
     */
    override fun isCurrent(vs: ValueSetExpansion): Boolean = sendPresentCurrentQuery(vs)

    /**
     * write a query checking the presence, send it to the host
     * check if it has errors. If it has data, a concept system matching the url
     * and version is already present (and current)
     *
     * @param vs the ValueSet to use
     * @return true if the concept system is present and current
     */
    private fun sendPresentCurrentQuery(vs: ValueSetExpansion): Boolean {
        val ql4MdrQuery = presenceQuery(vs)
        val response = sendGraphQlQuery(ql4MdrQuery.toString())
        val parsedJson = JSONQ(response.body().byteInputStream())
        val hasNoErrors = "errors" !in parsedJson.JSON().names()
        val isCurrent = !parsedJson.find("data.conceptSystem").isNull
        return response.statusCode() == HttpStatus.SC_OK && isCurrent && hasNoErrors
    }

    /**
     * send a string-encoded GraphQL query to the GraphQL endpoint
     * via HTTP POST and Content-Type "application/graphql".
     * This is supported by the reference implementation, but other implementations
     * of QL4MDR may need to wrap the query in a JSON object, or use HTTP GET instead.
     * Refer to https://graphql.org/learn/serving-over-http/ for details
     *
     * @param queryString the encoded query string
     * @return the HTTP response
     */
    private fun sendGraphQlQuery(queryString: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl(""))
            .header("Content-Type", "application/graphql")
            .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
            .POST(HttpRequest.BodyPublishers.ofString(queryString))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    /**
     * create the concept system via QL4MDR
     *
     * @param vs the VS to convert
     * @return true if successful
     */
    override fun create(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return convertedValueSet.result?.let {
            sendGraphQlQuery(it).statusCode() == HttpStatus.SC_OK
        } ?: false
    }

    /**
     * update the concept system via QL4MDR
     *
     * @param vs the VS to convert
     * @return true if successful
     */
    override fun update(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return convertedValueSet.result?.let {
            sendGraphQlQuery(it).statusCode() == HttpStatus.SC_OK
        } ?: false
    }
}