package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import de.uzl.itcr.termicron.ingest.ValueSetIngestPipeline
import de.uzl.itcr.termicron.synchronization.SynchronizationOutcome
import de.uzl.itcr.termicron.synchronization.SynchronizationPipeline
import de.uzl.itcr.termicron.synchronization.files.FileMdrSynchronization

/**
 * the main pipeline, with ingest, synchronization and authentication
 *
 * @property ingestPipeline the ingest pipeline as configured by the user
 * @property synchronizationPipeline the synchronization pipeline as configured by the user
 * @property authenticationConfiguration the authentication configuration as configured by the user
 */
data class ConversionPipeline(
    var ingestPipeline: ValueSetIngestPipeline? = null,
    var synchronizationPipeline: SynchronizationPipeline? = null,
    var authenticationConfiguration: AuthenticationConfiguration? = null
) {

    /**
     * check if the pipeline is configured end-to-end (ingest and synchronization configured, authentication if required)
     *
     * @return true if the pipeline is configured E2E
     */
    fun isConfigured(): Boolean = when {
        ingestPipeline == null -> false
        synchronizationPipeline == null -> false
        authenticationConfiguration == null -> when (synchronizationPipeline?.synchronizationHandler) {
            is FileMdrSynchronization -> true //files don't need authentication!
            else -> false
        }
        else -> true
    }

    /**
     * run the synchronization pipeline in a streaming fashion
     *
     * @return a list of outcomes, keyed by the canonical URL of the resource
     */
    @Throws(IllegalStateException::class)
    fun runConversionPipeline(): List<Pair<String, SynchronizationOutcome>>? {
        if (this.isConfigured()) {
            ingestPipeline!!.runStreaming()?.let {
                val synchronizationResult = synchronizationPipeline!!.runSynchronization(it)
                ingestPipeline!!.cleanup()
                return synchronizationResult
            } ?: return null
        } else {
            throw IllegalStateException("the conversion pipeline is not properly configured!")
        }
    }
}