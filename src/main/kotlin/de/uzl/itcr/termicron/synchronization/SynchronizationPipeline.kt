package de.uzl.itcr.termicron.synchronization

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation

/**
 * the synchronization pipeline with a concrete MdrSynchronization handler
 *
 * @property synchronizationHandler the handler
 */
class SynchronizationPipeline(
    val synchronizationHandler: MdrSynchronization,
) {
    /**
     * synchronize a sequence of ValueSets to the MDR using the configured provider,
     * in a streaming fashion
     *
     * @param valueSets the sequence of ValueSets
     * @return a list of pairs, first titles (canonical URLs in case of expansion errors), second synchronization outcomes
     */
    fun runSynchronization(valueSets: Sequence<ValueSetExpansionOperation>): List<Pair<String, SynchronizationOutcome>> =
        valueSets.asIterable().map {
            when {
                it.valueSetExpansion != null -> "'${it.valueSetExpansion.title}' v'${it.valueSetExpansion.businessVersion}'" to synchronizationHandler.synchronize(
                    it.valueSetExpansion
                )
                else -> "'${it.canonicalUrl}'" to SynchronizationOutcome.ERROR
            }
        }

    fun handlerIsValidEndpoint() = synchronizationHandler.validateEndpoint()
}