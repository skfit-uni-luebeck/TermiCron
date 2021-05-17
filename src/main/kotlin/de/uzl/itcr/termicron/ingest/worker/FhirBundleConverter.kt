package de.uzl.itcr.termicron.ingest.worker

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation
import de.uzl.itcr.termicron.ingest.FhirIngestProvider
import org.hl7.fhir.r4.model.Bundle

/**
 * convert a FHIR bundle to ValueSetExpansionOperations
 *
 * @constructor
 * create a FhirBundleConverter
 *
 * @param ingestProvider the ingest provider to use
 */
class FhirBundleConverter(
    ingestProvider: FhirIngestProvider
) {

    /**
     * converter instance for the bundle entries
     */
    private val valueSetConverter = FhirValueSetConverter(ingestProvider)

    /**
     * convert a bundle of resources using the FhirValueSetConverter instance
     *
     * @param resourcesToConvert the bundle to convert
     * @return a sequence of ValueSetExpansionOperations, which can be provided to the downstream
     * workers.
     */
    fun convertBundle(resourcesToConvert: Bundle): Sequence<ValueSetExpansionOperation> {
        return resourcesToConvert.entry.asSequence()
            .map { valueSetConverter.convertBundleEntry(it, resourcesToConvert) }
    }

}