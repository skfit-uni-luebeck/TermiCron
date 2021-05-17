package de.uzl.itcr.termicron.ingest.worker

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation
import de.uzl.itcr.termicron.ingest.FhirIngestProvider
import org.hl7.fhir.r4.model.Bundle

class FhirBundleConverter(
    ingestProvider: FhirIngestProvider
) {

    private val valueSetConverter = FhirValueSetConverter(ingestProvider)

    fun convertBundle(resourcesToConvert: Bundle): Sequence<ValueSetExpansionOperation> {
        return resourcesToConvert.entry.asSequence()
            .map { valueSetConverter.convertBundleEntry(it, resourcesToConvert) }
    }

}