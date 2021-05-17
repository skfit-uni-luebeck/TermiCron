package de.uzl.itcr.termicron.ingest.snomedctecl

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.ingest.FhirUtilities
import de.uzl.itcr.termicron.ingest.addAcceptHeader
import org.hl7.fhir.r4.model.ValueSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.time.Instant
import java.util.*

@Suppress("HttpUrlsUsage")
class EclQuery(
    val ecl: String,
    terminologyServerEndpoint: String,
    private val snomedCtEdition: String,
    private val snomedCtVersion: String?,
    private val valueSetName: String,
    private val valueSetTitle: String,
    private val valueSetVersion: String,
    private val outputDirectory: File?,
    val fhirContext: FhirContext
) {

    private val logger: Logger = LoggerFactory.getLogger(EclQuery::class.java)

    private val eclUrlEncoded: String get() = URLEncoder.encode(ecl.trim(), "UTF-8")

    val httpClient: HttpClient = HttpClient.newBuilder().build()

    private val fhirUtilities = FhirUtilities(httpClient, fhirContext)

    private val endpoint = URI.create("${terminologyServerEndpoint.trimEnd('/')}/")

    private fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun buildRequestUri(): URI {
        val snomedVersionQualifier = snomedCtVersion?.let { "/version/$it" } ?: ""
        val snomedIdentifier =
            urlEncode("http://snomed.info/sct/$snomedCtEdition$snomedVersionQualifier")
        logger.debug("Using SNOMED CT version $snomedIdentifier for expansion")
        val path = "ValueSet/\$expand?url=$snomedIdentifier?fhir_vs=ecl/$eclUrlEncoded"
        logger.debug("Using request path $path")
        return endpoint.resolve(path)
    }

    fun requestExpansion(): ValueSet {
        val requestUri = buildRequestUri().also {
            logger.info("Expanding ECL expression at: $it")
        }
        val request = HttpRequest.newBuilder(requestUri).addAcceptHeader().build()
        val valueSet =
            fhirUtilities.executeFhirStatementParsing<ValueSet>(request)//requestFromServerAsStream(httpClient, requestUri)
        //val valueSet = interpretResponseAsValueSet(response.body())
        //val valueSet = fhirContext.newJsonParser().parseResource(response.body()) as ValueSet
        return valueSet.apply {
            title = valueSetTitle
            name = valueSetName
            date = Date.from(Instant.now())
            version = valueSetVersion
            experimental = true
        }.also {
            writeValueSet(it)
        }
    }

    private fun writeValueSet(valueSet: ValueSet) {
        if (this.outputDirectory != null) {
            try {
                val outFile = outputDirectory.resolve("ValueSet-ECL_${valueSetName}_${valueSetVersion}.json")
                fhirContext.newJsonParser()
                    .setPrettyPrint(true)
                    .encodeResourceToWriter(valueSet, outFile.bufferedWriter())
                logger.info("Wrote expanded FHIR ValueSet to ${outFile.absolutePath}")
            } catch (e: IOException) {
                logger.error("Error writing expanded ECL expression to file")
            }
        } else {
            logger.info("No FHIR output directory was specified, not writing expanded file.")
        }
    }


}
