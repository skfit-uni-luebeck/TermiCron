package de.uzl.itcr.termicron.ingest

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation
import de.uzl.itcr.termicron.ingest.worker.FhirBundleConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ValueSetIngestPipeline(
    val ingestProvider: FhirIngestProvider,
    val cleanup: Boolean
) {

    private val logger: Logger = LoggerFactory.getLogger(ValueSetIngestPipeline::class.java)

    private val bundleConverter = FhirBundleConverter(ingestProvider)

    /**
     * determine which ValueSets to synchronize and produce a stream from those
     */
    fun runStreaming(): Sequence<ValueSetExpansionOperation>? {
        val resourcesToConvert = ingestProvider.retrieveResourcesToConvert() ?: return null
        return bundleConverter.convertBundle(resourcesToConvert)
    }

    fun cleanup() {
        if (cleanup) {
            logger.warn("cleaning up temporary artifacts")
            ingestProvider.cleanupTemporaryArtifacts()
        }
    }

    enum class FhirResourceTypes(val resourceName: String) {
        CODE_SYSTEM("CodeSystem"),
        VALUE_SET("ValueSet"),
        BUNDLE("Bundle"),
        OTHER_FHIR_RESOURCE("Other")
    }

    enum class BundleLinkRelations(val relationName: String) {
        CANONICAL("canonical"),
        CODESYSTEM("CodeSystem"), //TODO fix unused link, because it sucks...
        VALUESET("ValueSet")
    }

}
