package de.uzl.itcr.termicron.synchronization

import de.uzl.itcr.termicron.catalogmodel.ValueSetExpansion

/**
 * an abstract class for synchronization procedures using the configured output driver
 */
abstract class MdrSynchronization {

    /**
     * entry point for the pipeline. Synchronizes the provides VS:
     * if it is present and current, no update is required.
     * If it is only present, update it.
     * Else, create it.
     *
     * @param vs the VS to output/synchronize
     * @return a SynchronizationOutcome enum member
     */
    fun synchronize(vs: ValueSetExpansion): SynchronizationOutcome {
        val isPresent = isPresent(vs) //store once to prevent multiple requests
        return when {
            isPresent && isCurrent(vs) -> SynchronizationOutcome.NO_NEED
            isPresent -> if (update(vs)) SynchronizationOutcome.UPDATED else SynchronizationOutcome.ERROR //implies !isCurrent(vs)
            create(vs) -> SynchronizationOutcome.CREATED
            else -> SynchronizationOutcome.ERROR
        }
    }

    /**
     * check if the VS is present
     *
     * @param vs the VS
     * @return true if present
     */
    abstract fun isPresent(vs: ValueSetExpansion): Boolean

    /**
     * check if the VS is current
     *
     * @param vs the VS
     * @return true if present & current
     */
    abstract fun isCurrent(vs: ValueSetExpansion): Boolean

    /**
     * create the VS
     *
     * @param vs the VS
     * @return true if creation successful
     */
    abstract fun create(vs: ValueSetExpansion): Boolean

    /**
     * update an existing VS
     *
     * @param vs the VS
     * @return true if update successful
     */
    abstract fun update(vs: ValueSetExpansion): Boolean
}