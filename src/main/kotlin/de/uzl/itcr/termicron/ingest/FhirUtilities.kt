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

class FhirUtilities(val httpClient: HttpClient, val fhirContext: FhirContext) {

    val logger: Logger = LoggerFactory.getLogger(FhirUtilities::class.java)

    @Suppress("SpellCheckingInspection")
    enum class KnownFhirServer(val softwareNameFragment: String) {
        ONTOSERVER("ontoserver"),
        SNOWSTORM("snowstorm"),
        HAPIFHIR("hapi"),
        VONK("vonk"),
        REFERENCESERVER("reference"),
        OTHER("other")
    }

    private fun String.cleanUri(): URI = URI.create(this.trimEnd('/') + "/")

    fun getServerSoftware(uri: URI): KnownFhirServer {
        endpointServerMap[uri]?.let { return it }
        val metadataEndpoint = uri.resolve("metadata")
        val capabilityStatement = executeFhirStatementParsing<CapabilityStatement>(metadataEndpoint)
        val softwareName = capabilityStatement.software.name ?: "other"
        val detectedFhirServer = values()
            .find { softwareName.toLowerCase().contains(it.softwareNameFragment) }
            ?: OTHER
        //logger.info("Detected FHIR Server $detectedFhirServer at: $uri")
        when (detectedFhirServer) {
            SNOWSTORM -> logger.warn("IHTSDO Snowstorm only supports SNOMED CT expansion, and is not suitable for uses other than ECL expansion!")
            HAPIFHIR, VONK -> logger.warn("HAPI FHIR and Vonk provide only limited support for terminology operations, be careful.")
            REFERENCESERVER -> logger.warn("Grahame's Test server is likely not suited for use with complex terminology operations, be careful")
            else -> logger.info("Detected FHIR Server $detectedFhirServer at $uri")
        }
        endpointServerMap.putIfAbsent(uri, detectedFhirServer)
        return detectedFhirServer
    }

    fun validateUriIsFhirServerEndpoint(uriString: String): URI {
        val cleanUri = uriString.cleanUri()
        if (endpointServerMap.containsKey(cleanUri)) return cleanUri
        getServerSoftware(cleanUri)
        return cleanUri
    }

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

    inline fun <reified T : Resource> executeFhirStatementParsing(uri: URI): T =
        HttpRequest.newBuilder(uri).addAcceptHeader().GET().build().let {
            executeFhirStatementParsing(it)
        }

    companion object {
        private val endpointServerMap: MutableMap<URI, KnownFhirServer> = mutableMapOf()
    }
}

open class TerminologyConversionError(message: String, e: Exception? = null) : Exception(message, e)

class FhirServerError(message: String, val outcome: OperationOutcome? = null, e: Exception? = null) :
    TerminologyConversionError(message, e)