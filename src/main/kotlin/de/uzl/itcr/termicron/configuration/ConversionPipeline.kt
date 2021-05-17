package de.uzl.itcr.termicron.configuration

import de.uzl.itcr.termicron.authentication.AuthenticationConfiguration
import de.uzl.itcr.termicron.ingest.ValueSetIngestPipeline
import de.uzl.itcr.termicron.synchronization.SynchronizationOutcome
import de.uzl.itcr.termicron.synchronization.SynchronizationPipeline
import de.uzl.itcr.termicron.synchronization.files.FileMdrSynchronization

data class ConversionPipeline(
    var ingestPipeline: ValueSetIngestPipeline? = null,
    var synchronizationPipeline: SynchronizationPipeline? = null,
    var authenticationConfiguration: AuthenticationConfiguration? = null
) {

    fun isConfigured(): Boolean = when {
        ingestPipeline == null -> false
        synchronizationPipeline == null -> false
        authenticationConfiguration == null -> when (synchronizationPipeline?.synchronizationHandler) {
            is FileMdrSynchronization -> true //files don't need authentication!
            else -> false
        }
        else -> true
    }

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