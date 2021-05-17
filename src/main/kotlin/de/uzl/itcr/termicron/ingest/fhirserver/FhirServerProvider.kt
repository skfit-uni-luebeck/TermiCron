package de.uzl.itcr.termicron.ingest.fhirserver

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import de.uzl.itcr.termicron.StaticHelpers
import de.uzl.itcr.termicron.ingest.*
import de.uzl.itcr.termicron.ingest.worker.ValueSetTsExpander
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ValueSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient

/**
 * resolve resources from a FHIR Terminology Server
 * @property endpoint URI the URI endpoint of your terminology server
 * @property bundleId String the id of the bundle to use for synchronisation
 * @property httpClientInitializer [@kotlin.ExtensionFunctionType] Function1<Builder, Unit>?
 *  you can pass a function block that configures the http client to talk to your terminology server - handle authentication here!
 * @property httpClient HttpClient the http client to use when communicating with the TS
 */
class FhirServerProvider(
    endpoint: String,
    private val bundleId: String,
    fhirContext: FhirContext,
    private val httpClientInitializer: (HttpClient.Builder.() -> Unit)? = null,
) : FhirIngestProvider(fhirContext) {

    /**
     * the logger instance for this class
     */
    private val logger: Logger = LoggerFactory.getLogger(FhirServerProvider::class.java)

    /**
     * the HTTP Client to use
     */
    private val httpClient: HttpClient = StaticHelpers.httpClient(httpClientInitializer)

    /**
     * a FHIR helper for the required operations
     */
    private val fhirUtilities = FhirUtilities(httpClient, fhirContext)

    /**
     * the endpoint of the FHIR TS
     */
    private val endpoint = fhirUtilities.validateUriIsKnownFhirServer(endpoint)

    override fun supportsExpansion(): Boolean = true

    /**
     * expand a FHIR VS using the provided TS
     *
     * @param valueSet the VS to expand
     * @return the VS with expansion
     */
    @Throws(UnsupportedOperationException::class, FhirServerError::class)
    override fun expandValueSet(valueSet: ValueSet): ValueSet =
        ValueSetTsExpander(httpClientInitializer, endpoint.toString(), fhirContext).expand(valueSet)

    @Throws(FhirServerError::class, BundleValidationError::class)
    override fun retrieveResourcesToConvert(): Bundle {
        val requestUri = endpoint.resolve("Bundle/${bundleId}").also {
            logger.info("retrieving bundle from $it")
        }
        try {
            val receivedBundle = fhirUtilities.executeFhirStatementParsing<Bundle>(requestUri)
            val bundleValidation = validateBundleSatisfiesRequirements(receivedBundle)
            if (bundleValidation != null) throw BundleValidationError(bundleValidation)
            return receivedBundle
        } catch (e: DataFormatException) {
            throw FhirServerError(
                message = "HAPI was unable to parse the resource at $requestUri as a Bundle",
                outcome = null,
                e = e
            )
        }
    }

    override fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource {
        val bundleComponent = bundle.entry.find { it.getLink(relation.relationName).url == url }
            ?: throw NoSuchElementException("no resource with ${relation.relationName} = $url could be found in the bundle")
        return when (bundleComponent.hasResource()) {
            true -> bundleComponent.resource.also {
                logger.info("using resource from bundle for canonical: $url")
            }
            else -> retrieveResourceByAlternate(bundleComponent.getResourceLink()).also {
                logger.info("requesting by alternate for: $url from: ${bundleComponent.getResourceLink()}")
            }
        }
    }


    private fun retrieveResourceByAlternate(resourceLink: String): Resource {
        val resourceUri = URI.create(resourceLink).let { uri: URI ->
            return@let when {
                uri.isAbsolute -> uri
                else -> endpoint.resolve(resourceLink)
            }
        }
        return fhirUtilities.executeFhirStatementParsing(resourceUri)
    }
}