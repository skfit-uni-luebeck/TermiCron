package de.uzl.itcr.termicron.ingest

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.DataFormatException
import de.uzl.itcr.termicron.ingest.FhirUtilities.KnownFhirServer.*
import org.hl7.fhir.r4.model.CapabilityStatement
import org.hl7.fhir.r4.model.OperationOutcome
import org.hl7.fhir.r4.model.Resource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

/**
 * helper class to talk to FHIR HTTP endpoints
 *
 * @property httpClient the HttpClient to use
 * @property fhirContext the HAPI FHIR context
 */
class FhirUtilities(val httpClient: HttpClient, val fhirContext: FhirContext) {

    /**
     * the logger instance for this class
     */
    val logger: Logger = LoggerFactory.getLogger(FhirUtilities::class.java)

    /**
     * a enum class for known FHIR servers, used for determining quirks re. SNOMED CT PCEs
     *
     * @property softwareNameFragment a fragment in the servers's capabilityStatement that somewhat uniquely idenfies
     * this implementation
     */
    @Suppress("SpellCheckingInspection")
    enum class KnownFhirServer(val softwareNameFragment: String) {
        ONTOSERVER("ontoserver"),
        SNOWSTORM("snowstorm"),
        HAPIFHIR("hapi"),
        VONK("vonk"),
        REFERENCESERVER("reference"),
        OTHER("other")
    }

    /**
     * determine the server implementation for a specified FHIR URI endpoint
     *
     * @param uri the URI to verify
     * @return the KnownFhirServer enum member
     */
    fun getServerSoftware(uri: URI): KnownFhirServer? = try {
        endpointServerMap[uri]?.let { return it }
        val metadataEndpoint = uri.resolve("metadata")
        val capabilityStatement = executeFhirStatementParsing<CapabilityStatement>(metadataEndpoint)
        val softwareName = capabilityStatement.software.name ?: "other"
        val detectedFhirServer = values()
            .find { softwareName.lowercase(Locale.getDefault()).contains(it.softwareNameFragment) }
            ?: OTHER
        if (detectedFhirServer == OTHER && capabilityStatement.software.name != null) {
            logger.warn("The FHIR server '${capabilityStatement.software.name}' is not known")
        }
        when (detectedFhirServer) {
            SNOWSTORM -> logger.warn("IHTSDO Snowstorm only supports SNOMED CT expansion, and is not suitable for uses other than ECL expansion!")
            HAPIFHIR, VONK -> logger.warn("HAPI FHIR and Vonk provide only limited support for terminology operations, be careful.")
            REFERENCESERVER -> logger.warn("Grahame's Test server is likely not suited for use with complex terminology operations, be careful")
            else -> logger.info("Detected FHIR Server $detectedFhirServer at $uri")
        }
        endpointServerMap.putIfAbsent(uri, detectedFhirServer)
        detectedFhirServer
    } catch (e: Exception) {
        null
    }

    /**
     * check whether a URI is a known FHIR server. Checks the software from the metadata as a side effect
     *
     * @param uriString the URI string
     * @return the URI string as a validated URI
     */
    fun validateUriIsKnownFhirServer(uriString: String): URI {
        val cleanUri = uriString.cleanUri()
        if (endpointServerMap.containsKey(cleanUri)) return cleanUri
        getServerSoftware(cleanUri)
        return cleanUri
    }

    /**
     * execute a request against the provided FHIR server, and parse to the specified
     * type parameter using HAPI FHIR
     *
     * @param T the resource to parse as
     * @param httpRequest the HttpRequest to execute
     * @return the parsed resource as T
     */
    @Throws(FhirServerError::class)
    inline fun <reified T : Resource> executeFhirStatementParsing(httpRequest: HttpRequest): T {
        val stringResponse =
            httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()).body()
        try {
            return when (T::class) {
                Resource::class -> fhirContext.newJsonParser().parseResource(stringResponse)
                else -> fhirContext.newJsonParser().parseResource(T::class.java, stringResponse)
            } as T
        } catch (e: DataFormatException) {
            logger.error("error parsing FHIR resource as ${T::class.java.simpleName}")
            var outcome: OperationOutcome? = null
            try {
                outcome = fhirContext.newJsonParser().parseResource(OperationOutcome::class.java, stringResponse)
                logger.error("FHIR Server returned a OperationOutcome with this error: ${outcome.issueFirstRep.diagnostics}")
            } catch (e2: DataFormatException) {
                logger.error("The response by the server could neither be parsed as a ${T::class.java.simpleName} nor as as OperationOutcome.")
                logger.error("The endpoint ${httpRequest.uri()} is likely not a FHIR server (did you forget '/fhir'?)")
            }
            throw FhirServerError(message = "FHIR Server operation was unsuccessful", outcome = outcome, e = e)
        }
    }

    /**
     * execute a request against the provided FHIR server by GET via uri, and parse to the specified
     * type parameter using HAPI FHIR
     *
     * @param T the resource to parse as
     * @param uri the (absolute) URI to GET from
     * @return the parsed resource
     */
    inline fun <reified T : Resource> executeFhirStatementParsing(uri: URI): T =
        HttpRequest.newBuilder(uri).addAcceptHeader().GET().build().let {
            executeFhirStatementParsing(it)
        }

    companion object {
        /**
         * the list of known servers by URIs
         */
        private val endpointServerMap: MutableMap<URI, KnownFhirServer> = mutableMapOf()
    }
}

/**
 * an exception that occurred during conversion of a FHIR resource
 *
 * @constructor
 * instantiates an Exception
 *
 * @param message the descriptive message
 * @param e the inner Exception
 */
open class TerminologyConversionError(message: String, e: Exception? = null) : Exception(message, e)

/**
 * an exception that occurred talking to a FHIR TS endpoint
 *
 * @property outcome the OperationOutcome encapsulated here
 * @constructor
 * instantiates an TerminologyConversionError
 *
 * @param message the descriptive message
 * @param e the inner Exception
 */
class FhirServerError(message: String, val outcome: OperationOutcome? = null, e: Exception? = null) :
    TerminologyConversionError(message, e)



/**
 * clean this String as a URI: make sure exactly one "/" is at the end
 *
 * @return this String as "clean" URI
 */
fun String.cleanUri(): URI = URI.create(this.trimEnd('/') + "/")