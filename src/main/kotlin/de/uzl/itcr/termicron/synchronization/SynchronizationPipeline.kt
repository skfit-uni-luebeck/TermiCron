package de.uzl.itcr.termicron.synchronization

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansionOperation

data class SynchronizationPipeline(
    //val mdrConfiguration: MdrConfiguration,
    val synchronizationHandler: MdrSynchronization,
) {
    fun runSynchronization(valueSets: Sequence<ValueSetExpansionOperation>): List<Pair<String, SynchronizationOutcome>> =
        valueSets.asIterable().map {
            when {
                it.valueSetExpansion != null -> "'${it.valueSetExpansion.title}' v'${it.valueSetExpansion.businessVersion}'" to synchronizationHandler.synchro(
                    it.valueSetExpansion
                )
                else -> "'${it.canonicalUrl}'" to SynchronizationOutcome.ERROR
            }
        }
}
