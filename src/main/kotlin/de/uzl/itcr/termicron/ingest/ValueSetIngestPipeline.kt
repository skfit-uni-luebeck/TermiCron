package de.uzl.itcr.termicron.ingest

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation
import de.uzl.itcr.termicron.ingest.worker.FhirBundleConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * pipeline for the ingest of FHIR resources into TermiCron
 *
 * @property ingestProvider the respective ingest provider that provides FHIR files via some mechanism
 * @property cleanup whether cleanup should occur after ingest
 */
class ValueSetIngestPipeline(
    val ingestProvider: FhirIngestProvider,
    val cleanup: Boolean
) {

    /**
     * the logger for this class
     */
    private val logger: Logger = LoggerFactory.getLogger(ValueSetIngestPipeline::class.java)

    /**
     * an instance of FhirBundleConverter that makes use of the configured ingest provider
     */
    private val bundleConverter = FhirBundleConverter(ingestProvider)

    /**
     * determine which ValueSets to synchronize and produce a stream from those
     */
    fun runStreaming(): Sequence<ValueSetExpansionOperation>? {
        val resourcesToConvert = ingestProvider.retrieveResourcesToConvert() ?: return null
        return bundleConverter.convertBundle(resourcesToConvert)
    }

    /**
     * cleanup temporary files, if configured in the pipeline, i.e. by the user
     */
    fun cleanup() {
        if (cleanup) {
            logger.warn("cleaning up temporary artifacts")
            ingestProvider.cleanupTemporaryArtifacts()
        }
    }

    /**
     * enum class for FHIR resource types supported by TermiCron. All other FHIR resources are assigned to OTHER_FHIR_RESOURCE
     *
     * @property resourceName the resource name as specified by FHIR, e.g. "CodeSystem" with capitalization
     */
    enum class FhirResourceTypes(val resourceName: String) {
        CODE_SYSTEM("CodeSystem"),
        VALUE_SET("ValueSet"),
        BUNDLE("Bundle"),
        OTHER_FHIR_RESOURCE("Other")
    }

    /**
     * enum class for the links within a TermiCron bundle
     *
     * @property relationName the name of the relation in bundle.entry[].links[]
     */
    enum class BundleLinkRelations(val relationName: String) {
        CANONICAL("canonical"),
        CODESYSTEM("CodeSystem"),
        VALUESET("ValueSet")
    }

}
