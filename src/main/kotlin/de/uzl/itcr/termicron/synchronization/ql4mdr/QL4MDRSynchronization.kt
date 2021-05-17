package de.uzl.itcr.termicron.synchronization.ql4mdr

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.TermiCronConsoleApplication
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.configuration.CxxMdrConfiguration
import de.uzl.itcr.termicron.configuration.QL4MDRConfiguration
import de.uzl.itcr.termicron.output.MdrOutputResult
import de.uzl.itcr.termicron.output.cxx.CxxMdrOutputRest
import de.uzl.itcr.termicron.output.ql4mdr.QL4MDRMdrOutput
import de.uzl.itcr.termicron.output.ql4mdr.ql4mdr
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import ninja.sakib.jsonq.JSONQ
import org.apache.http.HttpStatus
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class QL4MDRSynchronization(
    private val authDriver: MdrAuthenticationDriver,
    private val mdrConfiguration: QL4MDRConfiguration,
    private val outputRest: QL4MDRMdrOutput
) : MdrSynchronization() {

    val logger = LoggerFactory.getLogger(QL4MDRSynchronization::class.java)
    private val client: HttpClient = StaticHelpers.httpClient()

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

    override fun isPresent(vs: ValueSetExpansion): Boolean {
        val ql4MdrQuery = presenceQuery(vs)
        val response = sendGraphQlQuery(ql4MdrQuery.toString())
        val parsedJson = JSONQ(response.body().byteInputStream())
        val hasNoErrors = "errors" !in parsedJson.JSON().names()
        val isPresent = !parsedJson.find("data.conceptSystem").isNull
        return (response.statusCode() == HttpStatus.SC_OK && isPresent && hasNoErrors)
    }

    override fun isCurrent(vs: ValueSetExpansion): Boolean {
        val ql4MdrQuery = presenceQuery(vs)
        val response = sendGraphQlQuery(ql4MdrQuery.toString())
        val parsedJson = JSONQ(response.body().byteInputStream())
        val hasNoErrors = "errors" !in parsedJson.JSON().names()
        val isCurrent = !parsedJson.find("data.conceptSystem").isNull
        return (response.statusCode() == HttpStatus.SC_OK && isCurrent && hasNoErrors)
    }

    private fun sendGraphQlQuery(queryString: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl(""))
            .header("Content-Type", "application/graphql")
            .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
            .POST(HttpRequest.BodyPublishers.ofString(queryString))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    override fun create(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return sendGraphQlQuery(convertedValueSet.result).statusCode() == HttpStatus.SC_OK
    }

    override fun update(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return sendGraphQlQuery(convertedValueSet.result).statusCode() == HttpStatus.SC_OK
    }
}