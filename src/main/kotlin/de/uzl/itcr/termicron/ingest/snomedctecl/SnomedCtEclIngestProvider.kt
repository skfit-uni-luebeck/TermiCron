package de.uzl.itcr.termicron.ingest.snomedctecl

import ca.uhn.fhir.context.FhirContext
import de.uzl.itcr.termicron.ingest.FhirIngestProvider
import de.uzl.itcr.termicron.ingest.ValueSetIngestPipeline
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Resource
import org.hl7.fhir.r4.model.ValueSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import kotlin.NoSuchElementException

/**
 * ingest using SNOMED CT ECL query expression
 *
 * @property eclQuery the query
 * @constructor
 * implements a FhirIngestProvider
 *
 * @param fhirContext the HAPI FHIR context
 */
@Suppress("UsePropertyAccessSyntax")
class SnomedCtEclIngestProvider(
    private val eclQuery: EclQuery,
    fhirContext: FhirContext
) :
    FhirIngestProvider(fhirContext) {

    /**
     * the logger instance for this class
     */
    private val logger: Logger = LoggerFactory.getLogger(SnomedCtEclIngestProvider::class.java)

    override fun retrieveResourcesToConvert(): Bundle? {
        try {
            val expandedVs = eclQuery.requestExpansion()
            return Bundle().apply {
                type = Bundle.BundleType.COLLECTION
                timestamp = Date.from(Instant.now())
                addEntry().apply {
                    addLink()
                        .setRelation(ValueSetIngestPipeline.BundleLinkRelations.CANONICAL.relationName)
                        .setUrl(expandedVs.url)
                    addLink()
                        .setRelation(ValueSetIngestPipeline.BundleLinkRelations.VALUESET.relationName)
                        .setUrl(expandedVs.url)
                    fullUrl = expandedVs.url
                    resource = expandedVs
                }
            }
        } catch (e: Exception) {
            logger.error("Error expanding ECL expression '${eclQuery.ecl}'")
            return null
        }
    }

    override fun getResourceByUrlAndLinkRelation(
        url: String,
        relation: ValueSetIngestPipeline.BundleLinkRelations,
        bundle: Bundle
    ): Resource {
        return bundle.entry.find { it.fullUrl == url }?.resource
            ?: throw NoSuchElementException("There is no expanded VS $url that has been requested via ECL")
    }

    override fun supportsExpansion(): Boolean = true

    override fun expandValueSet(valueSet: ValueSet): ValueSet = valueSet
}