@file:Suppress("HttpUrlsUsage", "UsePropertyAccessSyntax")

package de.uzl.itcr.termicron.ingest.worker

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.ingest.FhirUtilities
import de.uzl.itcr.termicron.ingest.FhirServerError
import de.uzl.itcr.termicron.ingest.addAcceptHeader
import de.uzl.itcr.termicron.ingest.addPayloadHeader
import org.hl7.fhir.r4.model.Parameters
import org.hl7.fhir.r4.model.ValueSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.net.http.HttpRequest

class ValueSetTsExpander(
    httpClientInitializer: (HttpClient.Builder.() -> Unit)? = null,
    endpoint: String,
    private val fhirContext: FhirContext
) {

    private val logger: Logger = LoggerFactory.getLogger(ValueSetTsExpander::class.java)

    //private val endpoint = URI.create("${endpoint.trimEnd('/')}/")
    private val httpClient: HttpClient = StaticHelpers.httpClient(httpClientInitializer)

    private val fhirUtilities = FhirUtilities(httpClient, fhirContext)
    private val endpoint = fhirUtilities.validateUriIsFhirServerEndpoint(endpoint)

    @Throws(FhirServerError::class)
    fun expand(valueSet: ValueSet): ValueSet {
        val requestUri = endpoint.resolve("ValueSet/\$expand").also {
            //logger.info("expanding ValueSet ${valueSet.name} at $it")
        }
        checkForPCE(valueSet)
        val bodyString: String = when (fhirUtilities.getServerSoftware(endpoint)) {
            FhirUtilities.KnownFhirServer.SNOWSTORM -> {
                val params = Parameters().apply {
                    addParameter()
                        .setName("valueSet")
                        .setResource(valueSet)
                }
                fhirContext.newJsonParser().encodeResourceToString(params)
            }
            else -> fhirContext.newJsonParser().encodeResourceToString(valueSet)
        }
        val expandRequest = HttpRequest.newBuilder()
            .uri(requestUri)
            .POST(HttpRequest.BodyPublishers.ofString(bodyString))
            .addAcceptHeader()
            .addPayloadHeader()
            .build()
        //return requestFromServerParseFhir(httpClient, expandRequest, fhirContext)
        return fhirUtilities.executeFhirStatementParsing(expandRequest)
    }

    private fun checkForPCE(valueSet: ValueSet) {
        val include = valueSet.compose.include ?: return
        val hasPce = include.any {
            it.system == "http://snomed.info/sct" && it.hasConcept() && it.concept!!.any { c ->
                c.code.contains(':') || c.code.contains(
                    "+"
                )
            }
        }
        if (!hasPce) return
        logger.warn("The ValueSet ${valueSet.url} ('${valueSet.name}') has SNOMED CT postcoordinated expressions in its definition.")
        when (val software = fhirUtilities.getServerSoftware(endpoint)) {
            FhirUtilities.KnownFhirServer.ONTOSERVER -> {
                logger.warn("Ontoserver requires that PCEs are provided in a CodeSystem supplement. If this is not present, PCEs in ValueSets will not be included in the expansion.")
                logger.warn("Please verify that PCEs are provided in a CodeSystem supplement and referenced in the ValueSet.")
                logger.warn("Refer to https://ontoserver.csiro.au/docs/6/postcoordination.html for more information.")
            }
            FhirUtilities.KnownFhirServer.SNOWSTORM -> {
                logger.warn("Snowstorm may not fully support PCEs in ValueSet expansions. The expansion may be incomplete")
            }

            FhirUtilities.KnownFhirServer.HAPIFHIR, FhirUtilities.KnownFhirServer.VONK ->
                logger.error("$software does not support SNOMED CT PCEs and results will be incomplete!")
            else -> logger.warn("$software may not support SNOMED CT PCEs.")
        }
    }

}