package de.uzl.itcr.termicron.synchronization.cxx

import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.authentication.MdrAuthenticationDriver
import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion
import de.uzl.itcr.termicron.configuration.CxxMdrConfiguration
import de.uzl.itcr.termicron.configuration.MdrConfiguration
import de.uzl.itcr.termicron.output.cxx.CxxMdrOutputRest
import de.uzl.itcr.termicron.synchronization.MdrSynchronization
import org.apache.http.HttpStatus
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class CxxMdrSynchronization(
    private val authDriver: MdrAuthenticationDriver,
    private val mdrConfiguration: CxxMdrConfiguration,
    private val outputRest: CxxMdrOutputRest
) : MdrSynchronization() {

    private val log = LoggerFactory.getLogger(CxxMdrSynchronization::class.java)

    private val client: HttpClient = StaticHelpers.httpClient()

/*    override fun synchro(vs: ValueSetExpansion): SynchronizationOutcome {
        val isPresent = isPresent(vs) //store once to prevent multiple requests
        return when {
            isPresent && isCurrent(vs) -> SynchronizationOutcome.NO_NEED
            isPresent -> if (update(vs)) SynchronizationOutcome.UPDATED else SynchronizationOutcome.ERROR //implies !isCurrent(vs)
            create(vs) -> SynchronizationOutcome.CREATED
            else -> SynchronizationOutcome.ERROR
        }
    }*/

    override fun isPresent(vs: ValueSetExpansion): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl("/catalogs/catalog?code=${vs.titleUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))
            .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return (response.statusCode() == HttpStatus.SC_OK).also { log.info("expanded VS '${vs.title} @ version '${vs.businessVersion}' is ${if (it) "present" else "not present"}") }
    }

    override fun isCurrent(vs: ValueSetExpansion): Boolean {
        val request = HttpRequest.newBuilder()
            .uri(mdrConfiguration.buildApiUrl("catalogs/catalog?code=${vs.titleUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))
            .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.discarding())
        return (response.statusCode() == HttpStatus.SC_OK).also { log.info("expanded VS '${vs.title} @ version '${vs.businessVersion}' is ${if (it) "current" else "not current"}") }
    }

    override fun create(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return if (convertedValueSet.success) {
            val request = HttpRequest.newBuilder()
                .uri(mdrConfiguration.buildApiUrl("catalogs/catalog"))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
                .POST(HttpRequest.BodyPublishers.ofString(convertedValueSet.result))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            // TODO Error Handling: no right to create catalogs
/*            println(response.body())
            println(response.statusCode())*/
            (response.statusCode() == HttpStatus.SC_CREATED).also {
                log.info("expanded VS '${vs.title} @ version '${vs.businessVersion}' was ${if (it) "successfully" else "not successfully"} created")
            }
        } else {
            false
        }
    }

    override fun update(vs: ValueSetExpansion): Boolean {
        val convertedValueSet = outputRest.outputCatalog(vs)
        return if (convertedValueSet.success) {
            val request = HttpRequest.newBuilder()
                .uri(mdrConfiguration.buildApiUrl("catalogs/catalog?code=${vs.titleUrlEncoded}&version=${vs.businessVersionUrlEncoded}"))
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("Authorization", authDriver.currentCredential().encodeCredentialToAuthorizationHeader())
                .PUT(HttpRequest.BodyPublishers.ofString(convertedValueSet.result))
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
/*            println(response.body())
            println(response.statusCode())*/
            (response.statusCode() == HttpStatus.SC_OK).also {
                log.info("expanded VS '${vs.title} @ version '${vs.businessVersion}' was ${if (it) "successfully" else "not successfully"} updated")
            }
        } else {
            false
        }
    }
}