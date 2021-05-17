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

/**
 * expand a FHIR ValueSet using a TS
 *
 * @property fhirContext the HAPI FHIR context
 * @constructor
 * create a new ValueSetTsExpander
 *
 * @param httpClientInitializer a function to pass to the HttpClient builder, for authentication etc.
 * @param endpoint the TS endpoint
 */
class ValueSetTsExpander(
    httpClientInitializer: (HttpClient.Builder.() -> Unit)? = null,
    endpoint: String,
    private val fhirContext: FhirContext
) {

    /**
     * the logger instance for this class
     */
    private val logger: Logger = LoggerFactory.getLogger(ValueSetTsExpander::class.java)

    /**
     * the HTTP client for this class
     */
    private val httpClient: HttpClient = StaticHelpers.httpClient(httpClientInitializer)

    /**
     * the FhirUtilities instance for this class
     */
    private val fhirUtilities = FhirUtilities(httpClient, fhirContext)

    /**
     * FHIR TS endpoint for this class
     */
    private val endpoint = fhirUtilities.validateUriIsKnownFhirServer(endpoint)

    /**
     * expand a VS using the respective FHIR TS
     *
     * @param valueSet the ValueSet to expand
     * @return the expanded FHIR VS
     */
    @Throws(FhirServerError::class)
    fun expand(valueSet: ValueSet): ValueSet {
        val requestUri = endpoint.resolve("ValueSet/\$expand")
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
        return fhirUtilities.executeFhirStatementParsing(expandRequest)
    }

    /**
     * check if a FHIR VS contains SNOMED CT post-coordinated expressions.
     * These require special attention, so warning are generated, based on the detected FHIR TS implementations
     *
     * @param valueSet the ValueSet to verify
     */
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
        logger.warn("The ValueSet ${valueSet.url} ('${valueSet.name}') has SNOMED CT postcoordinated expressions (PCEs) in its definition.")
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
            else -> logger.warn("$software may not fully support SNOMED CT PCEs.")
        }
    }
}